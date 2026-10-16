# Rendering
Both renderers hook into `RenderLevelStageEvent` at `AFTER_BLOCK_ENTITIES` and read from `ClientClaimCache` to know which chunks are claimed and by whom.

### BorderRenderer
Draws colored lines along chunk edges where territory borders another team or unclaimed land. The line hugs the terrain — it only draws on exposed block faces (solid block with air adjacent).

- Only renders chunks that are currently loaded on the client (`hasChunk` filter), so only visible land is processed
- Scans the full world height (`minBuildHeight` to `maxBuildHeight`), so borders remain visible when flying above terrain
- Three quad types: **horizontal** (top/bottom of blocks), **outward** (vertical face toward neighbor), **lateral** (bridges height gaps between columns)
- Horizontal and lateral quads fade from `BORDER_ALPHA` at the edge to transparent inward (`LINE_THICKNESS = 0.25` blocks wide)
- `Z_FIGHT_OFFSET` nudges geometry slightly off block surfaces to prevent flickering

### BorderWallRenderer
Draws a full-height transparent wall at chunk borders that fades in as the player approaches.

- `REVEAL_RADIUS = 8.0` — wall is invisible beyond this distance
- `INNER_RADIUS = 1.5` — wall fades out as the player walks into it (plane-distance based)
- `MAX_ALPHA = 0.35` — peak opacity, keeps walls subtle
- Per-vertex alpha based on 3D distance to player, multiplied by plane fade for smooth pass-through

### SubClaimWandRenderer
Registered client-only via `@EventBusSubscriber`. Draws the yellow (`1,1,0`) outlines used while the Sub-Claim Wand(subclaims.md) is in the main hand.

- Hooks `RenderHighlightEvent.Block` to cancel the vanilla block outline and draw a 1-block box (inflated `0.002` against z-fighting) around the hovered block
- On `RenderLevelStageEvent` (`AFTER_BLOCK_ENTITIES`) draws the current selection box from `SubClaimWandClientHandler.getEffectiveBox()` while a selection is in progress (phase > 0)
- Both use `RenderType.lines()` (`disableDepthTest`, `lineWidth 32`) so the box shows through terrain; the event PoseStack has the camera at the origin, so each draw translates by `-camera`

### SubClaimBorderRenderer
Registered on the client event bus in `Capitol`. Draws every synced sub-claim from `ClientSubClaimCache` while the wand is held; returns early if the wand is not in hand or the cache is empty.

- `REVEAL_RADIUS = 24.0` — a face is only emitted when the player is within this distance of it (axis distance, checked per face)
- `INNER_RADIUS = 2.0` / `MAX_ALPHA = 0.15` — same proximity fade as the walls: `MAX_ALPHA * (1 - dist / REVEAL_RADIUS)`, tapered again below `INNER_RADIUS`
- Renders translucent yellow **quads** (blended, `depthMask(false)`) plus a brighter full yellow **outline** of each `AABB` (`lineWidth 32`, no depth test)
- Only sub-claims in the current dimension are rendered; the wall/outline is drawn directly from the synced inclusive `min`/`max` corners

## Sub-Claim Selection
Selection state lives on the wand's `CUSTOM_DATA` tag (`SubClaimWand`), so it survives view/world changes and is per-item.

- **Phases:** `Phase 0` (idle)-> right-click sets `First` corner and moves to `Phase 1` -> right-click sets `Second` corner and moves to `Phase 2` -> right-click reopens the naming screen. `isFoil()` returns true whenever phase > 0 as a visual cue.
- **Box storage:** corners are stored as absolute `BoxMin*/BoxMax*` coords; `BoxCoords` normalises min/max on construction. Legacy wands that stored offsets (`OffPos*/OffNeg*`) are migrated on read.
- **Live box:** `getEffectiveBox()` handles both phases — phase 1 builds an inclusive min/max from the first corner and the block under the cursor (`hitResult`), phase 2 reads the stored box.
- **Ctrl+scroll reshape:** scroll pushes/pulls the looked-at face by 1 block; each side is clamped to keep a 1-block gap from its opposite so the box can't cross over (`SubClaimWandClientHandler.onMouseScroll`).
- **Looked-at face:** a ray/`AABB` entry test picks the face the eye ray enters; if the eye is inside the box the dominant look axis is used instead (`getLookedAtFace`).
- **Cancel:** shift right-click while phase > 0 clears the selection. The naming screen clears it on both confirm and close, so a dismissed screen can't leave the wand stuck in phase 2.