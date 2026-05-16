package controlador;

import controlador.automation.ServerAutomationService;
import modelo.Server;
import modelo.automation.AutomationActionType;
import modelo.automation.AutomationIntervalUnit;
import modelo.automation.AutomationTriggerType;
import modelo.automation.ServerAutomationRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ServerAutomationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void validateRule_rejectsCommandAutomationWithoutCommand() {
        RecordingGestorServidores gestor = new RecordingGestorServidores(tempDir.resolve("servers.json").toFile());
        ServerAutomationService service = new ServerAutomationService(gestor);
        ServerAutomationRule rule = new ServerAutomationRule();
        rule.setTriggerType(AutomationTriggerType.APP_START);
        rule.setActionType(AutomationActionType.COMMAND);
        rule.setCommand(" ");

        assertThat(service.validateRule(rule).valid()).isFalse();

        service.close();
    }

    @Test
    void resolveIntervalDuration_supportsSecondMinuteAndHourRules() {
        ServerAutomationRule secondRule = new ServerAutomationRule();
        secondRule.setIntervalAmount(30);
        secondRule.setIntervalUnit(AutomationIntervalUnit.SECONDS);

        ServerAutomationRule minuteRule = new ServerAutomationRule();
        minuteRule.setIntervalAmount(15);
        minuteRule.setIntervalUnit(AutomationIntervalUnit.MINUTES);

        ServerAutomationRule hourRule = new ServerAutomationRule();
        hourRule.setIntervalAmount(2);
        hourRule.setIntervalUnit(AutomationIntervalUnit.HOURS);

        assertThat(ServerAutomationService.resolveIntervalDuration(secondRule)).isEqualTo(Duration.ofSeconds(30));
        assertThat(ServerAutomationService.resolveIntervalDuration(minuteRule)).isEqualTo(Duration.ofMinutes(15));
        assertThat(ServerAutomationService.resolveIntervalDuration(hourRule)).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void validateRule_supportsRelativeCommandTriggers() {
        RecordingGestorServidores gestor = new RecordingGestorServidores(tempDir.resolve("servers.json").toFile());
        ServerAutomationService service = new ServerAutomationService(gestor);

        ServerAutomationRule beforeRestart = rule(AutomationActionType.COMMAND, "say reinicio pronto");
        beforeRestart.setTriggerType(AutomationTriggerType.BEFORE_RESTART);
        beforeRestart.setIntervalAmount(10);
        beforeRestart.setIntervalUnit(AutomationIntervalUnit.MINUTES);

        ServerAutomationRule afterStart = rule(AutomationActionType.COMMAND, "say servidor listo");
        afterStart.setTriggerType(AutomationTriggerType.AFTER_START);
        afterStart.setIntervalAmount(30);
        afterStart.setIntervalUnit(AutomationIntervalUnit.SECONDS);

        ServerAutomationRule invalidLifecycle = rule(AutomationActionType.STOP, null);
        invalidLifecycle.setTriggerType(AutomationTriggerType.BEFORE_STOP);
        invalidLifecycle.setIntervalAmount(5);
        invalidLifecycle.setIntervalUnit(AutomationIntervalUnit.MINUTES);

        ServerAutomationRule invalidAfterUnit = rule(AutomationActionType.COMMAND, "say listo");
        invalidAfterUnit.setTriggerType(AutomationTriggerType.AFTER_START);
        invalidAfterUnit.setIntervalAmount(1);
        invalidAfterUnit.setIntervalUnit(AutomationIntervalUnit.HOURS);

        assertThat(service.validateRule(beforeRestart).valid()).isTrue();
        assertThat(service.validateRule(afterStart).valid()).isTrue();
        assertThat(service.validateRule(invalidLifecycle).valid()).isFalse();
        assertThat(service.validateRule(invalidAfterUnit).valid()).isFalse();

        service.close();
    }

    @Test
    void validateRule_rejectsStartIntervalsAndLargeRelativeOffsets() {
        RecordingGestorServidores gestor = new RecordingGestorServidores(tempDir.resolve("servers.json").toFile());
        Server server = new Server();
        ServerAutomationService service = new ServerAutomationService(gestor);

        ServerAutomationRule startInterval = rule(AutomationActionType.START, null);
        startInterval.setTriggerType(AutomationTriggerType.INTERVAL);
        startInterval.setIntervalAmount(5);
        startInterval.setIntervalUnit(AutomationIntervalUnit.MINUTES);

        ServerAutomationRule restartAnchor = rule(AutomationActionType.RESTART, null);
        restartAnchor.setTriggerType(AutomationTriggerType.INTERVAL);
        restartAnchor.setIntervalAmount(10);
        restartAnchor.setIntervalUnit(AutomationIntervalUnit.MINUTES);

        ServerAutomationRule beforeRestart = rule(AutomationActionType.COMMAND, "say reinicio pronto");
        beforeRestart.setTriggerType(AutomationTriggerType.BEFORE_RESTART);
        beforeRestart.setIntervalAmount(10);
        beforeRestart.setIntervalUnit(AutomationIntervalUnit.MINUTES);
        server.setAutomationRules(new ArrayList<>(List.of(restartAnchor, beforeRestart)));

        assertThat(service.validateRule(startInterval).valid()).isFalse();
        assertThat(service.validateRule(server, beforeRestart).valid()).isFalse();

        service.close();
    }

    @Test
    void nextDailyExecution_rollsPastTimesToNextDay() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-16T10:15:00Z"), ZoneId.of("UTC"));

        assertThat(ServerAutomationService.nextDailyExecution(LocalTime.of(10, 30), clock))
                .isEqualTo(Instant.parse("2026-05-16T10:30:00Z"));
        assertThat(ServerAutomationService.nextDailyExecution(LocalTime.of(10, 0), clock))
                .isEqualTo(Instant.parse("2026-05-17T10:00:00Z"));
    }

    @Test
    void nextRelativeDailyExecution_appliesBeforeAndAfterOffsets() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-16T10:15:00Z"), ZoneId.of("UTC"));

        assertThat(ServerAutomationService.nextRelativeDailyExecution(
                LocalTime.of(10, 30),
                Duration.ofMinutes(10),
                false,
                clock
        )).isEqualTo(Instant.parse("2026-05-16T10:20:00Z"));
        assertThat(ServerAutomationService.nextRelativeDailyExecution(
                LocalTime.of(10, 0),
                Duration.ofMinutes(30),
                true,
                clock
        )).isEqualTo(Instant.parse("2026-05-16T10:30:00Z"));
        assertThat(ServerAutomationService.nextRelativeDailyExecution(
                LocalTime.of(10, 0),
                Duration.ofMinutes(10),
                false,
                clock
        )).isEqualTo(Instant.parse("2026-05-17T09:50:00Z"));
    }

    @Test
    void executeRuleNow_dispatchesLifecycleAndCommandActions() throws Exception {
        RecordingGestorServidores gestor = new RecordingGestorServidores(tempDir.resolve("servers.json").toFile());
        Server server = new Server();
        server.setDisplayName("Servidor automatizado");
        gestor.setListaServidores(List.of(server));
        ServerAutomationService service = new ServerAutomationService(
                gestor,
                Executors.newSingleThreadScheduledExecutor(),
                Clock.systemUTC()
        );

        service.executeRuleNow(server, rule(AutomationActionType.START, null)).get(2, TimeUnit.SECONDS);
        server.setServerProcess(new FakeProcess(true));
        service.executeRuleNow(server, rule(AutomationActionType.STOP, null)).get(2, TimeUnit.SECONDS);
        service.executeRuleNow(server, rule(AutomationActionType.RESTART, null)).get(2, TimeUnit.SECONDS);
        service.executeRuleNow(server, rule(AutomationActionType.COMMAND, "say hola")).get(2, TimeUnit.SECONDS);

        assertThat(gestor.actions).containsExactly("start", "stop", "restart", "command:say hola:false");

        service.close();
    }

    @Test
    void executeRuleNow_skipsActionsThatCannotUseCurrentRuntimeState() throws Exception {
        RecordingGestorServidores gestor = new RecordingGestorServidores(tempDir.resolve("servers.json").toFile());
        Server server = new Server();
        ServerAutomationService service = new ServerAutomationService(
                gestor,
                Executors.newSingleThreadScheduledExecutor(),
                Clock.systemUTC()
        );

        service.executeRuleNow(server, rule(AutomationActionType.STOP, null)).get(2, TimeUnit.SECONDS);
        service.executeRuleNow(server, rule(AutomationActionType.RESTART, null)).get(2, TimeUnit.SECONDS);
        service.executeRuleNow(server, rule(AutomationActionType.COMMAND, "say hola")).get(2, TimeUnit.SECONDS);
        server.setServerProcess(new FakeProcess(true));
        service.executeRuleNow(server, rule(AutomationActionType.START, null)).get(2, TimeUnit.SECONDS);

        assertThat(gestor.actions).isEmpty();

        service.close();
    }

    @Test
    void stopIntervalSchedulesOnlyWhenServerIsRunning() {
        RecordingGestorServidores gestor = new RecordingGestorServidores(tempDir.resolve("servers.json").toFile());
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        Server server = new Server();
        ServerAutomationRule stopAfterUptime = rule(AutomationActionType.STOP, null);
        stopAfterUptime.setTriggerType(AutomationTriggerType.INTERVAL);
        stopAfterUptime.setIntervalAmount(1);
        stopAfterUptime.setIntervalUnit(AutomationIntervalUnit.MINUTES);
        server.setAutomationRules(new ArrayList<>(List.of(stopAfterUptime)));
        gestor.setListaServidores(List.of(server));
        ServerAutomationService service = new ServerAutomationService(gestor, executor, Clock.systemUTC());

        service.start();
        assertThat(executor.getQueue()).isEmpty();

        server.setServerProcess(new FakeProcess(true));
        service.onServerStateChanged(server);

        assertThat(executor.getQueue()).hasSize(1);

        service.close();
    }

    @Test
    void getReglasAutomatizacion_ignoresNullRulesAndSaveDeduplicatesIds() {
        RecordingGestorServidores gestor = new RecordingGestorServidores(tempDir.resolve("servers.json").toFile());
        Server server = new Server();
        ServerAutomationRule first = rule(AutomationActionType.COMMAND, "say uno");
        ServerAutomationRule second = rule(AutomationActionType.COMMAND, "say dos");
        second.setId(first.getId());
        gestor.reemplazarReglasAutomatizacion(server, List.of(first, second));

        assertThat(gestor.getReglasAutomatizacion(server))
                .extracting(ServerAutomationRule::getId)
                .doesNotHaveDuplicates();

        server.setAutomationRules(new ArrayList<>());
        server.getAutomationRules().add(null);
        assertThat(gestor.getReglasAutomatizacion(server)).isEmpty();
    }

    private static ServerAutomationRule rule(AutomationActionType actionType, String command) {
        ServerAutomationRule rule = new ServerAutomationRule();
        rule.setEnabled(true);
        rule.setTriggerType(AutomationTriggerType.APP_START);
        rule.setActionType(actionType);
        rule.setCommand(command);
        return rule;
    }

    private static final class RecordingGestorServidores extends GestorServidores {
        private final List<String> actions = new ArrayList<>();

        private RecordingGestorServidores(File jsonFile) {
            super(jsonFile);
        }

        @Override
        public synchronized void iniciarServidor(Server server) throws IOException {
            actions.add("start");
        }

        @Override
        public void safePararServidor(Server server) {
            actions.add("stop");
        }

        @Override
        public void reiniciarServidor(Server server) throws IOException {
            actions.add("restart");
        }

        @Override
        public void mandarComando(Server server, String comando, boolean mostrarEnConsola) {
            actions.add("command:" + comando + ":" + mostrarEnConsola);
        }
    }

    private static final class FakeProcess extends Process {
        private final boolean alive;

        private FakeProcess(boolean alive) {
            this.alive = alive;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
