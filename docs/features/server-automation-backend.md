# Server Automation Backend

## Status

Implemented

## Feature

Added backend support for per-server automation rules that can start, stop, restart, or send console commands on program start, a daily time, a repeated second/minute/hour interval, or command-only relative lifecycle triggers.

## Motivation

The future Automation tab needs durable logic before its full UI is designed, so rule editing can later connect to a tested service API instead of duplicating lifecycle behavior.

## Solution

Automation rules are persisted on `modelo.Server`, normalized during legacy model migration, and managed through `GestorServidores` APIs. `ServerAutomationService` schedules enabled rules and dispatches actions through the existing server lifecycle and command methods. Relative command triggers are documented in `docs/features/automation-relative-triggers.md`.

The vertical navigation includes a lazy Automation tab using the CPU icon pair. The full rule editing UI is documented in `docs/features/automation-tab-ui.md`.

## Files Changed

- `src/main/java/modelo/Server.java`
- `src/main/java/modelo/automation/`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/controlador/Main.java`
- `src/main/java/controlador/automation/`
- `src/main/java/vista/PanelAutomatizacion.java`
- `src/main/java/vista/VentanaPrincipal.java`
- `src/main/java/vista/VentanaPrincipalNavigationBuilder.java`
- `src/main/java/vista/VentanaPrincipalRightContentBuilder.java`
- `src/main/resources/doraicons/cpu.svg`
- `src/test/java/controlador/ServerAutomationServiceTest.java`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=ServerAutomationServiceTest" test`

## Detailed Process

- `docs/features/process/server-automation-backend.md`

## Follow-Up Notes

Future automation expansions should keep using `GestorServidores.guardarReglaAutomatizacion(...)`, `reemplazarReglasAutomatizacion(...)`, `eliminarReglaAutomatizacion(...)`, and `getAutomationService().validateRule(...)` so persistence and scheduling remain centralized.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
- `docs/features/automation-tab-ui.md`
- `docs/features/automation-relative-triggers.md`
