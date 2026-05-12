# GestorServidores Has Too Many Responsibilities

## Status

Pending

## Area

`controlador.GestorServidores`.

## Issue

`GestorServidores` handles server list persistence, selection, import/create UI flows, lifecycle/process behavior, backups/conversions, port dialogs, and platform orchestration. The class is difficult to navigate and easy to grow further.

## Desired Behavior

Server management responsibilities should be separated behind focused services while keeping the existing public flow stable.

## Evidence

- `src/main/java/controlador/GestorServidores.java`
- nested server creation wizard UI classes
- port occupied dialog logic
- preservation/conversion behavior

## Suggested Approach

Extract gradually when modifying related behavior:

- creation wizard UI/coordinator
- port conflict dialog
- backup/conversion preservation helper
- server persistence/reorder helper

Avoid a large one-shot rewrite.

## Verification

- `mvn -q -DskipTests compile`
- `mvn test -Dtest=GestorServidoresTest`
- Manual create/import/start smoke test if affected.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/platform-adapters-pipeline.md`
