# Label / Text Document Architecture (Canva-grade)

## Status
Accepted — Strangler Fig migration in progress.

## Context
Legacy `EditorLayer` mixed content, layout geometry, and effects. Multiple mutation paths
(`LabelViewModelDelegate`, gesture ops, `shapeFitFlow`, viewport bake) wrote
`shapeWidthPx` / `shapeHeightPx` independently, causing bounding-box drift, lost effects,
and frame/label desync.

## Decision
Introduce Document v2 under `ui/editor/document/`:

| Piece | Role |
|-------|------|
| `SceneNode` (`PureText`, `TextInShape`, `Shape`) | Typed objects |
| `TextContent` / `LayoutConstraints` / `StyleBag` | Separated concerns |
| `DocumentCommand` + `DocumentReducer` | Single mutation API (I1) |
| `LayoutEngine` + `LayoutPolicy` | Derived bounds (I2) |
| `EffectCompatibilityMatrix` | Effect rules (I5) |
| `EditorLayerBridge` | Bidirectional adapter |
| `DocumentStore` / `DocumentSession` | Snapshot + history + ViewModel facade |
| `DocumentRenderPipeline` | Unified draw entry (I6) |
| `LayerGroupSync` | Hard sync transform + box for FRAME+LABEL (I3) |

## Invariants
See plan I1–I7. Violations are architectural bugs, not UI hotfixes.

## Migration
1. Shadow: `DocumentSession.syncFromState` mirrors EditorState.
2. Cutover: text transform, font size, text-form preset, style templates dispatch via DocumentSession.
3. Remaining label/shape mutations migrate command-by-command.
4. `shapeFitFlow` removed — fit intents go through `LayoutEngine` (Document) or sync fit in disabled-Document fallbacks.

## Consequences
- UI must not call `layer.copy(shapeHeightPx = …)` outside layout/adapter.
- Templates apply atomic `StyleBag` replace.
- TextForm disables Elevation3D (deterministic v1).
