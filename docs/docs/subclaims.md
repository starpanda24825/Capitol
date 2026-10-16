# Sub-Claims
A sub-claim is a named 3D box **inside a team's already-claimed chunks**, owned by a single team member rather than the team. It lets one member carve out a private plot (a base, a machine, a farm) inside shared territory, with its own permission and protection overrides.

- Modelled by the `SubClaim` record: `id` (UUID), `teamId`, `name`, `dimension`, inclusive `min/max` XYZ, `ownerUuid`, `permissions` (bitfield) and `protections` (bitfield)
- The sub-claim's volume wins over the chunk/team check in `ProtectionManager`, so a sub-claim can tighten (or loosen) access inside its box
- Players select the box with the sub-claim-wand; the selection and rendering pipeline is documented in rendering.md

### Sub-Claim Wand
- Item `capitol:sub_claim_wand`, `stacksTo(1)`, durability 20; also adds `+35.5` main-hand block interaction range
- Right-click walks the selection phases, Ctrl+scroll reshapes the box, shift right-click cancels (see rendering.md#sub-claim-selection)
- On confirm, the client opens `SubClaimNamingScreen` (32-char name) and sends `C2SCreateSubClaim`; creating a sub-claim also costs 1 wand durability via `C2SDamageWand`

### Creation Rules
Creation is validated entirely server-side in `ServerPayloadHandler.handleCreateSubClaim`, so the client cannot bypass it:
- The player must be in a team with the `CLAIM_SUB_CLAIMS` permission; corners are normalised and the dimension must match the player's current dimension
- Every chunk the box touches must be claimed by the player's own team (sub-claims can't spill into wilderness or other teams)
- The box may not overlap any other sub-claim in that dimension, and a player can't reuse one of their own names (case-insensitive)
- New sub-claims start with **all** permissions and **all** protections enabled, owned by the creating player

### Permissions & Protections
- Sub-claim access uses its own `permissions` bitfield: the owner always passes, everyone else is checked against the bits (`ProtectionManager.resolveSubClaimPermission`)
- `SubClaimProtection` is a separate bitfield toggled per sub-claim: `EXPLOSION`, `MOB_GRIEFING`, `FIRE`, `PISTON`, `FLUID_FLOW`, `CROP_TRAMPLING` (all enabled by default). See permissions.md

### Lifecycle
- Unclaiming a chunk deletes every sub-claim that extends into it (`deleteSubClaimsIntersectingChunk`); disbanding a team clears all its sub-claims via the FK cascade
- When a member leaves or is kicked, sub-claims they owned inside the team transfer to the team leader, renaming to `name(n)` on a name clash (`transferSubClaimsOnLeave`)
- The owner (or anyone with `MANAGE_TEAM`) can remove one with `/capitol subclaim delete <id>`; all paths broadcast `S2CSubClaimRemove` so clients drop it from `ClientSubClaimCache`

See commands.md for the `/capitol subclaim` command set.
