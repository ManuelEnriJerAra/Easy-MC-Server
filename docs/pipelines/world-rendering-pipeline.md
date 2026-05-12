# World Rendering Pipeline

Use this guide before editing MCA parsing, preview generation, render options, world storage analysis, or overlay/player markers.

## Core Classes

- `controlador.MCARenderer`
- `controlador.WorldDataReader`
- `controlador.world.PreviewRenderPreferences`
- `controlador.world.WorldPreviewCatalogService`
- `controlador.world.WorldPreviewOverlayService`
- `controlador.world.WorldPlayerDataService`
- `controlador.world.WorldStorageAnalyzer`
- `controlador.world.WorldFilesService`
- `vista.PanelMundo`

## MCA Render Flow

`MCARenderer` renders `.mca` region files into images.

High-level stages:

1. Validate and parse region coordinates.
2. Load MCA region/chunk data.
3. Sample top visible blocks and biome/water data.
4. Resolve colors and material profiles.
5. Paint region/world image.
6. Crop to visible bounds if enabled.
7. Draw optional markers.
8. Return image plus metadata/stats.

`RenderOptions` controls quality/performance behavior, including:

- pixels per block
- Y range
- height shading
- water subsurface shading
- biome coloring
- advanced material/water/biome coloring
- square crop preference
- world bounds
- spiral traversal

## Panel Preview Flow

`PanelMundo` owns the UI preview flow.

It:

- discovers candidate region files
- applies render preferences
- starts a background preview generation worker
- receives progress updates
- paints the preview image
- overlays spawn/player markers
- persists preview preferences in world metadata

Avoid duplicate render workers and keep UI mutations on the EDT.

## World Data Reading

`WorldDataReader` reads `level.dat` and exposes:

- version/data version
- storage layout
- active ticks
- last played
- seed
- spawn point
- game mode/difficulty/hardcore/commands
- weather/time
- datapacks/game rules

It handles legacy and namespaced overworld region paths.

## Storage And Player Data

`WorldStorageAnalyzer` computes world/player/stats bytes for UI display.

`WorldPlayerDataService` reads playerdata/stats style data to identify recent players and preview overlay points.

## Debugging Renderer

`MCARenderer` has debug logging behind the system property:

```text
easymc.mca.debug=true
```

Use it for render diagnostics without changing UI behavior.

## Tests

Relevant tests:

- `MCARendererTest`
- `MCARendererImageComparisonTest`
- `MCARendererVisualRegressionTest`
- `MCARendererRealWorldComparisonTest`
- `MCARendererBenchmarkTest`
- `WorldDataReaderTest`
- `WorldStorageAnalyzerTest`
- `WorldPreviewOverlayServiceTest`
- `PreviewRenderPreferencesTest`

Run targeted tests when changing render logic, NBT parsing, storage analysis, or preview preferences.
