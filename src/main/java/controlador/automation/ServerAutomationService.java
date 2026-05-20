package controlador.automation;

import controlador.AppErrorReporter;
import controlador.GestorServidores;
import modelo.Server;
import modelo.automation.AutomationActionType;
import modelo.automation.AutomationIntervalUnit;
import modelo.automation.AutomationTriggerType;
import modelo.automation.ServerAutomationRule;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ServerAutomationService implements AutoCloseable {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final GestorServidores gestorServidores;
    private final ScheduledExecutorService executor;
    private final Clock clock;
    private final ConcurrentMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private volatile boolean running;
    private long scheduleGeneration;

    public ServerAutomationService(GestorServidores gestorServidores) {
        this(
                gestorServidores,
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "server-automation-scheduler");
                    thread.setDaemon(true);
                    return thread;
                }),
                Clock.systemDefaultZone()
        );
    }

    public ServerAutomationService(GestorServidores gestorServidores,
                                   ScheduledExecutorService executor,
                                   Clock clock) {
        this.gestorServidores = Objects.requireNonNull(gestorServidores, "gestorServidores");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        refreshSchedules();
        runAppStartRules();
    }

    public synchronized void refreshSchedules() {
        scheduleGeneration++;
        cancelAllSchedules();
        if (!running) {
            return;
        }
        for (Server server : safeServers()) {
            scheduleServer(server);
        }
    }

    public synchronized void onServerRulesChanged(Server server) {
        if (!running || server == null) {
            return;
        }
        scheduleGeneration++;
        cancelServerSchedules(server);
        scheduleServer(server);
    }

    public synchronized void onServerStateChanged(Server server) {
        if (!running || server == null || server.getId() == null) {
            return;
        }
        cancelServerUptimeStopSchedules(server);
        if (isServerRunning(server)) {
            scheduleUptimeStopRules(server);
        }
    }

    public Future<?> executeRuleNow(Server server, ServerAutomationRule rule) {
        return executor.submit(() -> executeRule(server, rule));
    }

    public AutomationRuleValidation validateRule(ServerAutomationRule rule) {
        if (rule == null) {
            return AutomationRuleValidation.error("La automatización no existe.");
        }
        if (rule.getActionType() == null) {
            return AutomationRuleValidation.error("Selecciona una acción.");
        }
        if (rule.getTriggerType() == null) {
            return AutomationRuleValidation.error("Selecciona cuando se ejecuta.");
        }
        if (rule.getActionType() == AutomationActionType.COMMAND && isBlank(rule.getCommand())) {
            return AutomationRuleValidation.error("Indica el comando que se debe enviar.");
        }
        if (rule.getTriggerType() == AutomationTriggerType.DAILY_TIME && parseTime(rule.getTimeOfDay()) == null) {
            return AutomationRuleValidation.error("Indica una hora válida con formato HH:mm.");
        }
        if (rule.getTriggerType() == AutomationTriggerType.INTERVAL && resolveIntervalDuration(rule) == null) {
            return AutomationRuleValidation.error("Indica un intervalo válido.");
        }
        if (rule.getActionType() == AutomationActionType.START
                && rule.getTriggerType() == AutomationTriggerType.INTERVAL) {
            return AutomationRuleValidation.error("El encendido por intervalo no está disponible.");
        }
        if (isRelativeTrigger(rule.getTriggerType())) {
            if (rule.getActionType() != AutomationActionType.COMMAND) {
                return AutomationRuleValidation.error("Los disparadores relativos solo están disponibles para comandos.");
            }
            if (resolveIntervalDuration(rule) == null) {
                return AutomationRuleValidation.error("Indica un margen válido.");
            }
            if (rule.getTriggerType() == AutomationTriggerType.AFTER_START
                    && rule.getIntervalUnit() != AutomationIntervalUnit.SECONDS
                    && rule.getIntervalUnit() != AutomationIntervalUnit.MINUTES) {
                return AutomationRuleValidation.error("El margen después del encendido se mide en segundos o minutos.");
            }
        }
        return AutomationRuleValidation.ok();
    }

    public AutomationRuleValidation validateRule(Server server, ServerAutomationRule rule) {
        AutomationRuleValidation validation = validateRule(rule);
        if (!validation.valid()) {
            return validation;
        }
        if (!isRelativeTrigger(rule.getTriggerType()) || server == null || server.getAutomationRules() == null) {
            return validation;
        }
        Duration offset = resolveIntervalDuration(rule);
        AutomationActionType anchorAction = anchorActionFor(rule.getTriggerType());
        for (ServerAutomationRule anchorRule : server.getAutomationRules()) {
            if (!isRelativeAnchorRule(anchorRule, anchorAction)
                    || anchorRule.getTriggerType() != AutomationTriggerType.INTERVAL) {
                continue;
            }
            Duration anchorInterval = resolveIntervalDuration(anchorRule);
            if (anchorInterval != null && offset != null && !offset.minus(anchorInterval).isNegative()) {
                return AutomationRuleValidation.error("El margen debe ser menor que el intervalo de la automatización relacionada.");
            }
        }
        return validation;
    }

    public static Duration resolveIntervalDuration(ServerAutomationRule rule) {
        if (rule == null || rule.getIntervalAmount() == null || rule.getIntervalAmount() <= 0) {
            return null;
        }
        AutomationIntervalUnit unit = rule.getIntervalUnit();
        if (unit == null) {
            return null;
        }
        try {
            Duration duration = unit.toDuration(rule.getIntervalAmount());
            return duration.isNegative() || duration.isZero() ? null : duration;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public Instant nextDailyExecution(LocalTime time) {
        return nextDailyExecution(time, clock);
    }

    public static Instant nextDailyExecution(LocalTime time, Clock clock) {
        if (time == null) {
            return null;
        }
        Clock effectiveClock = clock == null ? Clock.systemDefaultZone() : clock;
        ZoneId zone = effectiveClock.getZone();
        LocalDate today = LocalDate.now(effectiveClock);
        LocalDateTime candidate = LocalDateTime.of(today, time);
        Instant now = Instant.now(effectiveClock);
        Instant candidateInstant = candidate.atZone(zone).toInstant();
        if (!candidateInstant.isAfter(now)) {
            candidateInstant = candidate.plusDays(1).atZone(zone).toInstant();
        }
        return candidateInstant;
    }

    public static Instant nextRelativeDailyExecution(LocalTime anchorTime,
                                                     Duration offset,
                                                     boolean after,
                                                     Clock clock) {
        if (anchorTime == null || offset == null || offset.isNegative() || offset.isZero()) {
            return null;
        }
        Clock effectiveClock = clock == null ? Clock.systemDefaultZone() : clock;
        ZoneId zone = effectiveClock.getZone();
        LocalDate day = LocalDate.now(effectiveClock);
        LocalDateTime candidate = LocalDateTime.of(day, anchorTime);
        candidate = after ? candidate.plus(offset) : candidate.minus(offset);
        Instant now = Instant.now(effectiveClock);
        Instant candidateInstant = candidate.atZone(zone).toInstant();
        while (!candidateInstant.isAfter(now)) {
            candidate = candidate.plusDays(1);
            candidateInstant = candidate.atZone(zone).toInstant();
        }
        return candidateInstant;
    }

    @Override
    public synchronized void close() {
        running = false;
        cancelAllSchedules();
        executor.shutdownNow();
    }

    private void scheduleServer(Server server) {
        if (server == null || server.getId() == null || server.getAutomationRules() == null) {
            return;
        }
        for (ServerAutomationRule rule : server.getAutomationRules()) {
            if (!isSchedulable(rule)) {
                continue;
            }
            if (rule.getTriggerType() == AutomationTriggerType.INTERVAL) {
                if (rule.getActionType() == AutomationActionType.STOP) {
                    if (isServerRunning(server)) {
                        scheduleUptimeStopRule(server.getId(), rule);
                    }
                } else {
                    scheduleIntervalRule(server.getId(), rule);
                }
            } else if (rule.getTriggerType() == AutomationTriggerType.DAILY_TIME) {
                scheduleDailyRule(server.getId(), rule.getId(), scheduleGeneration);
            }
        }
        scheduleRelativeRules(server);
    }

    private void scheduleUptimeStopRules(Server server) {
        if (server == null || server.getId() == null || server.getAutomationRules() == null) {
            return;
        }
        for (ServerAutomationRule rule : server.getAutomationRules()) {
            if (rule != null
                    && rule.getActionType() == AutomationActionType.STOP
                    && rule.getTriggerType() == AutomationTriggerType.INTERVAL
                    && isSchedulable(rule)) {
                scheduleUptimeStopRule(server.getId(), rule);
            }
        }
    }

    private void scheduleUptimeStopRule(String serverId, ServerAutomationRule rule) {
        Duration duration = resolveIntervalDuration(rule);
        if (duration == null) {
            return;
        }
        ScheduledFuture<?> future = executor.schedule(
                () -> executeRuleById(serverId, rule.getId()),
                duration.toMillis(),
                TimeUnit.MILLISECONDS
        );
        putScheduledTask(uptimeStopTaskKey(serverId, rule.getId()), future);
    }

    private void scheduleIntervalRule(String serverId, ServerAutomationRule rule) {
        Duration duration = resolveIntervalDuration(rule);
        if (duration == null) {
            return;
        }
        long millis = duration.toMillis();
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                () -> executeRuleById(serverId, rule.getId()),
                millis,
                millis,
                TimeUnit.MILLISECONDS
        );
        putScheduledTask(taskKey(serverId, rule.getId()), future);
    }

    private void scheduleDailyRule(String serverId, String ruleId, long generation) {
        if (generation != scheduleGeneration) {
            return;
        }
        ServerAutomationRule rule = findRule(serverId, ruleId);
        if (!isSchedulable(rule)) {
            return;
        }
        LocalTime time = parseTime(rule.getTimeOfDay());
        Instant next = nextDailyExecution(time);
        if (next == null) {
            return;
        }
        long delayMillis = Math.max(0L, Duration.between(Instant.now(clock), next).toMillis());
        ScheduledFuture<?> future = executor.schedule(() -> {
            executeRuleById(serverId, ruleId);
            synchronized (this) {
                if (running && generation == scheduleGeneration) {
                    scheduleDailyRule(serverId, ruleId, generation);
                }
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
        putScheduledTask(taskKey(serverId, ruleId), future);
    }

    private void scheduleRelativeRules(Server server) {
        if (server == null || server.getId() == null || server.getAutomationRules() == null) {
            return;
        }
        for (ServerAutomationRule rule : server.getAutomationRules()) {
            if (!isRelativeCommandRule(rule)) {
                continue;
            }
            if (!validateRule(server, rule).valid()) {
                continue;
            }
            AutomationActionType anchorAction = anchorActionFor(rule.getTriggerType());
            if (anchorAction == null) {
                continue;
            }
            for (ServerAutomationRule anchorRule : server.getAutomationRules()) {
                if (!isRelativeAnchorRule(anchorRule, anchorAction)) {
                    continue;
                }
                scheduleRelativeRule(server.getId(), rule, anchorRule);
            }
        }
    }

    private void scheduleRelativeRule(String serverId,
                                      ServerAutomationRule relativeRule,
                                      ServerAutomationRule anchorRule) {
        Duration offset = resolveIntervalDuration(relativeRule);
        if (offset == null || anchorRule == null || anchorRule.getTriggerType() == null) {
            return;
        }
        boolean after = relativeRule.getTriggerType() == AutomationTriggerType.AFTER_START;
        if (anchorRule.getTriggerType() == AutomationTriggerType.DAILY_TIME) {
            scheduleRelativeDailyRule(serverId, relativeRule.getId(), anchorRule.getId(), after, offset, scheduleGeneration);
        } else if (anchorRule.getTriggerType() == AutomationTriggerType.INTERVAL) {
            scheduleRelativeIntervalRule(serverId, relativeRule, anchorRule, after, offset);
        }
    }

    private void scheduleRelativeDailyRule(String serverId,
                                           String relativeRuleId,
                                           String anchorRuleId,
                                           boolean after,
                                           Duration offset,
                                           long generation) {
        if (generation != scheduleGeneration) {
            return;
        }
        ServerAutomationRule relativeRule = findRule(serverId, relativeRuleId);
        if (!isRelativeCommandRule(relativeRule)) {
            return;
        }
        ServerAutomationRule anchorRule = findRule(serverId, anchorRuleId);
        AutomationActionType anchorAction = anchorActionFor(relativeRule.getTriggerType());
        if (!isRelativeAnchorRule(anchorRule, anchorAction)
                || anchorRule.getTriggerType() != AutomationTriggerType.DAILY_TIME) {
            return;
        }
        LocalTime time = parseTime(anchorRule.getTimeOfDay());
        Instant next = nextRelativeDailyExecution(time, offset, after, clock);
        if (next == null) {
            return;
        }
        long delayMillis = Math.max(0L, Duration.between(Instant.now(clock), next).toMillis());
        ScheduledFuture<?> future = executor.schedule(() -> {
            executeRuleById(serverId, relativeRuleId);
            synchronized (this) {
                if (running && generation == scheduleGeneration) {
                    scheduleRelativeDailyRule(serverId, relativeRuleId, anchorRuleId, after, offset, generation);
                }
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
        putScheduledTask(taskKey(serverId, relativeRuleId, "relative:" + anchorRuleId), future);
    }

    private void scheduleRelativeIntervalRule(String serverId,
                                              ServerAutomationRule relativeRule,
                                              ServerAutomationRule anchorRule,
                                              boolean after,
                                              Duration offset) {
        Duration anchorInterval = resolveIntervalDuration(anchorRule);
        if (anchorInterval == null) {
            return;
        }
        long periodMillis = anchorInterval.toMillis();
        long offsetMillis = offset.toMillis();
        if (periodMillis <= 0L || offsetMillis <= 0L || offsetMillis >= periodMillis) {
            return;
        }
        long initialDelayMillis = after
                ? Math.addExact(periodMillis, offsetMillis)
                : beforeIntervalDelayMillis(periodMillis, offsetMillis);
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                () -> executeRuleById(serverId, relativeRule.getId()),
                initialDelayMillis,
                periodMillis,
                TimeUnit.MILLISECONDS
        );
        putScheduledTask(taskKey(serverId, relativeRule.getId(), "relative:" + anchorRule.getId()), future);
    }

    private void runAppStartRules() {
        for (Server server : safeServers()) {
            if (server == null || server.getAutomationRules() == null) {
                continue;
            }
            for (ServerAutomationRule rule : server.getAutomationRules()) {
                if (!Boolean.TRUE.equals(rule == null ? null : rule.getEnabled())) {
                    continue;
                }
                if (rule.getTriggerType() == AutomationTriggerType.APP_START && validateRule(rule).valid()) {
                    executor.execute(() -> executeRule(server, rule));
                }
            }
        }
    }

    private void executeRuleById(String serverId, String ruleId) {
        Server server = gestorServidores.getServerById(serverId);
        ServerAutomationRule rule = findRule(server, ruleId);
        executeRule(server, rule);
    }

    private void executeRule(Server server, ServerAutomationRule rule) {
        if (server == null || rule == null || !Boolean.TRUE.equals(rule.getEnabled())) {
            return;
        }
        AutomationRuleValidation validation = validateRule(server, rule);
        if (!validation.valid()) {
            server.appendConsoleLinea("[WARN] Automatización omitida: " + validation.message());
            return;
        }
        if (shouldSkipForRuntimeState(server, rule.getActionType())) {
            return;
        }

        String name = firstNonBlank(rule.getDisplayName(), rule.getActionType().name());
        server.appendConsoleLinea("[INFO] Ejecutando automatización: " + name + ".");
        try {
            switch (rule.getActionType()) {
                case START -> gestorServidores.iniciarServidor(server);
                case STOP -> gestorServidores.safePararServidor(server);
                case RESTART -> gestorServidores.reiniciarServidor(server);
                case COMMAND -> gestorServidores.mandarComando(server, rule.getCommand(), false);
            }
        } catch (IOException ex) {
            server.appendConsoleLinea("[ERROR] No se ha podido ejecutar la automatización: " + ex.getMessage());
            AppErrorReporter.report("No se ha podido ejecutar una automatización.", ex);
        } catch (RuntimeException ex) {
            server.appendConsoleLinea("[ERROR] Error en la automatización: " + ex.getMessage());
            AppErrorReporter.report("Error en una automatización.", ex);
        }
    }

    private ServerAutomationRule findRule(String serverId, String ruleId) {
        return findRule(gestorServidores.getServerById(serverId), ruleId);
    }

    private ServerAutomationRule findRule(Server server, String ruleId) {
        if (server == null || ruleId == null || server.getAutomationRules() == null) {
            return null;
        }
        for (ServerAutomationRule rule : server.getAutomationRules()) {
            if (rule != null && Objects.equals(rule.getId(), ruleId)) {
                return rule;
            }
        }
        return null;
    }

    private boolean isSchedulable(ServerAutomationRule rule) {
        return Boolean.TRUE.equals(rule == null ? null : rule.getEnabled())
                && rule.getTriggerType() != AutomationTriggerType.APP_START
                && !isRelativeTrigger(rule.getTriggerType())
                && validateRule(rule).valid();
    }

    private boolean isRelativeCommandRule(ServerAutomationRule rule) {
        return Boolean.TRUE.equals(rule == null ? null : rule.getEnabled())
                && isRelativeTrigger(rule.getTriggerType())
                && validateRule(rule).valid();
    }

    private boolean isRelativeAnchorRule(ServerAutomationRule rule, AutomationActionType actionType) {
        return Boolean.TRUE.equals(rule == null ? null : rule.getEnabled())
                && rule.getActionType() == actionType
                && (rule.getTriggerType() == AutomationTriggerType.DAILY_TIME
                || rule.getTriggerType() == AutomationTriggerType.INTERVAL)
                && validateRule(rule).valid();
    }

    private List<Server> safeServers() {
        List<Server> servers = gestorServidores.getListaServidores();
        return servers == null ? List.of() : new ArrayList<>(servers);
    }

    private void cancelServerSchedules(Server server) {
        if (server == null || server.getId() == null) {
            return;
        }
        String prefix = server.getId() + ":";
        for (String key : new ArrayList<>(scheduledTasks.keySet())) {
            if (key.startsWith(prefix)) {
                cancelTask(key);
            }
        }
    }

    private void cancelServerUptimeStopSchedules(Server server) {
        if (server == null || server.getId() == null) {
            return;
        }
        String prefix = server.getId() + ":uptime-stop:";
        for (String key : new ArrayList<>(scheduledTasks.keySet())) {
            if (key.startsWith(prefix)) {
                cancelTask(key);
            }
        }
    }

    private void cancelAllSchedules() {
        for (String key : new ArrayList<>(scheduledTasks.keySet())) {
            cancelTask(key);
        }
    }

    private void cancelTask(String key) {
        ScheduledFuture<?> future = scheduledTasks.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void putScheduledTask(String key, ScheduledFuture<?> future) {
        ScheduledFuture<?> previous = scheduledTasks.put(key, future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private static String taskKey(String serverId, String ruleId) {
        return serverId + ":" + ruleId;
    }

    private static String taskKey(String serverId, String ruleId, String suffix) {
        return taskKey(serverId, ruleId) + ":" + suffix;
    }

    private static String uptimeStopTaskKey(String serverId, String ruleId) {
        return serverId + ":uptime-stop:" + ruleId;
    }

    private static boolean isServerRunning(Server server) {
        return server != null
                && server.getServerProcess() != null
                && server.getServerProcess().isAlive();
    }

    private static boolean shouldSkipForRuntimeState(Server server, AutomationActionType actionType) {
        boolean running = isServerRunning(server);
        return switch (actionType) {
            case START -> running;
            case STOP, RESTART, COMMAND -> !running;
        };
    }

    private static boolean isRelativeTrigger(AutomationTriggerType triggerType) {
        return triggerType == AutomationTriggerType.BEFORE_STOP
                || triggerType == AutomationTriggerType.BEFORE_RESTART
                || triggerType == AutomationTriggerType.AFTER_START;
    }

    private static AutomationActionType anchorActionFor(AutomationTriggerType triggerType) {
        if (triggerType == AutomationTriggerType.BEFORE_STOP) {
            return AutomationActionType.STOP;
        }
        if (triggerType == AutomationTriggerType.BEFORE_RESTART) {
            return AutomationActionType.RESTART;
        }
        if (triggerType == AutomationTriggerType.AFTER_START) {
            return AutomationActionType.START;
        }
        return null;
    }

    private static long beforeIntervalDelayMillis(long periodMillis, long offsetMillis) {
        long remainder = offsetMillis % periodMillis;
        return remainder == 0L ? 0L : periodMillis - remainder;
    }

    private static LocalTime parseTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String first, String fallback) {
        return isBlank(first) ? fallback : first.trim();
    }
}
