# Database
Capitol uses SQLite (`capitol.db`) stored in the world save folder. `DatabaseManager` handles the connection lifecycle — init on server start, close on server stop. Pragmas: WAL mode, synchronous NORMAL, foreign keys ON.

### Schema
- **teams** — `id` (UUID text, PK), `name`, `color` (ARGB int), `created_at` (epoch ms)
- **team_members** — `team_id` + `player_uuid` (composite PK), `role_id` (base role, FK to `team_roles`), plus per-player override rows. FK to teams with CASCADE delete
- **team_roles** — `id` (autoincrement PK), `team_id`, `name`, `permissions` (bitfield), `color`, `position`, `system_key` (null for custom roles), `flags` (bit 0 = mailable)
- **member_roles** — `team_id` + `player_uuid` + `role_id` (composite PK), the extra roles stacked above a member's base role
- **chunks** — `dimension` + `chunk_x` + `chunk_z` (composite PK), `team_id`. FK to teams with CASCADE delete
- **sub_claims** — `id` (UUID text, PK), `team_id`, `name`, `dimension`, inclusive `min_x/min_y/min_z` + `max_x/max_y/max_z`, `owner_uuid`, `permissions` (bitfield), `protections` (bitfield). FK to teams with CASCADE delete. See [subclaims.md](subclaims.md)

Schema changes ship as numbered migrations in `DatabaseManager.runMigrations`; the `sub_claims` table is version **6** (creating it, or adding `owner_uuid`/`permissions`/`protections` to older tables).
- **mail** — `id` (autoincrement PK), `sender_uuid`/`sender_team_id`/`target_team_id` (nullable, plain text so mail outlives a disband), `subject`, `body`, `sent_at`, `external`
- **mail_recipients** — `mail_id` + `recipient_uuid` (composite PK), `is_read`, `deleted`. FK to `mail` with CASCADE delete

Other tables: `sub_claims` (owned protected areas), `capitol_blocks` (named/upgradable blocks plus mayors), `player_permissions` (per-team individual overrides), `wars`, `disband_requests`/`disband_approvals`. Schema migrations run in order inside a transaction, copy `capitol.db` to `capitol.db.bak-v<old>` first and bump `PRAGMA user_version` (currently **9**).

### Access Layer
`CapitolDatabase` (extends abstract `Database`) handles all CRUD operations via `PreparedStatement` with parameter binding:
- Team ops: `addTeam`, `removeTeam`, `getTeam`, `getPlayerTeam`
- Member ops: `addPlayerToTeam`, `removePlayerFromTeam`, `updatePlayerRole`, `getPlayerRole`
- Chunk ops: `claimChunk`, `unclaimChunk`, `getTeamChunks`, `unclaimAllChunks`
- Query ops: `hasChunkAt`, `getChunkOwner`, `getPermissionInChunk`, `isPlayerInTeam`
- Sub-claim ops: `addSubClaim`, `removeSubClaim`, `getSubClaim`, `getSubClaimAt`, `getSubClaimsOwnedBy`, `getTeamSubClaims`, `getAllSubClaims`, `deleteSubClaimsIntersectingChunk`, `toggleSubClaimPermission`, `toggleSubClaimProtection`, `isSubClaimOwner`, `updateSubClaimName`
