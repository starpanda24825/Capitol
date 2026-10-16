# Permissions
Permissions use a bitfield (`long`) where each bit is a specific action. **Bits 0-39 are used, 40-63 are free.** The `Permission` enum defines the flags grouped by `PermissionCategory` (Administrative, Block, Entity, Item, Redstone, Portal, Sub-claims, Mail, Disband). `Permission.categories()` returns them grouped for the GUI and `Permission.fromBits(bits)` expands a bitfield into an `EnumSet`.

- `Permission.of(BREAK_BLOCKS, PLACE_BLOCKS)` — combine into a bitfield
- `perm.add(bits)` / `perm.remove(bits)` — set/clear a bit
- `perm.toggle(bits)` — flip a bit
- `perm.hasPermission(bits)` — check if a flag is set

### Roles
Roles are rows in `team_roles`, identified by `system_key` when they are system roles (never by name): `owner` (computed full permissions, exactly one holder), `member` (baseline every member has), `default` (what non-members may do in the team's claims) and `mayor` (scoped to a capitol block's chunks). Custom roles have `system_key IS NULL` and can be created, edited, reordered and deleted.

Each member has one **base role** (`team_members.role_id`, owner or member) plus any number of **extra roles** (`member_roles`). Effective permissions are `owner ? all : OR(base, extras) | individual overrides`, resolved in one place by `CapitolDatabase.getEffectivePermissions`. `RoleManager` (backed by the pure `RoleRules` helpers) holds every hierarchy and permission rule and is called by both commands and packet handlers, so the rules can't drift apart.

### Sub-claims
- `CLAIM_SUB_CLAIMS` (`1L << 27`) gates creating sub-claims; it is checked in `ServerPayloadHandler.handleCreateSubClaim`
- A sub-claim stores its own `permissions` bitfield: its owner always passes, everyone else is checked against those bits (resolved in `ProtectionManager`)
- A sub-claim's permission check takes priority over the surrounding chunk's team check, so it can tighten or loosen access inside its box
- `SubClaimProtection` is a separate bitfield (`EXPLOSION`, `MOB_GRIEFING`, `FIRE`, `PISTON`, `FLUID_FLOW`, `CROP_TRAMPLING`) toggled per sub-claim with `/capitol subclaim protection`

### Enforcement
Permission checks are enforced in `PlayerInteractionEvents` on the server side; sub-claim protections are checked first in `ProtectionManager` for explosion, fire, piston, fluid flow, mob griefing and crop trampling. See [claim_protection.md](../to-do/claim_protection.md) for the implementation status checklist and [roles_test.md](../to-do/roles_test.md) for the role rules checklist.
