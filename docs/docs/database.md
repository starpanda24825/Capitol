# Database
Capitol uses SQLite (`capitol.db`) stored in the world save folder. `DatabaseManager` handles the connection lifecycle — init on server start, close on server stop. Pragmas: WAL mode, synchronous NORMAL, foreign keys ON.

### Schema
- **teams** — `id` (UUID text, PK), `name`, `color` (ARGB int), `created_at` (epoch ms)
- **team_members** — `team_id` + `player_uuid` (composite PK), `role` (text), `permissions` (bitfield int). FK to teams with CASCADE delete
- **chunks** — `dimension` + `chunk_x` + `chunk_z` (composite PK), `team_id`. FK to teams with CASCADE delete
- **sub_claims** — `id` (UUID text, PK), `team_id`, `name`, `dimension`, inclusive `min_x/min_y/min_z` + `max_x/max_y/max_z`, `owner_uuid`, `permissions` (bitfield), `protections` (bitfield). FK to teams with CASCADE delete. See [subclaims.md](subclaims.md)

Schema changes ship as numbered migrations in `DatabaseManager.runMigrations`; the `sub_claims` table is version **6** (creating it, or adding `owner_uuid`/`permissions`/`protections` to older tables).

### Access Layer
`CapitolDatabase` (extends abstract `Database`) handles all CRUD operations via `PreparedStatement` with parameter binding:
- Team ops: `addTeam`, `removeTeam`, `getTeam`, `getPlayerTeam`
- Member ops: `addPlayerToTeam`, `removePlayerFromTeam`, `updatePlayerRole`, `getPlayerRole`
- Chunk ops: `claimChunk`, `unclaimChunk`, `getTeamChunks`, `unclaimAllChunks`
- Query ops: `hasChunkAt`, `getChunkOwner`, `getPermissionInChunk`, `isPlayerInTeam`
- Sub-claim ops: `addSubClaim`, `removeSubClaim`, `getSubClaim`, `getSubClaimAt`, `getSubClaimsOwnedBy`, `getTeamSubClaims`, `getAllSubClaims`, `deleteSubClaimsIntersectingChunk`, `toggleSubClaimPermission`, `toggleSubClaimProtection`, `isSubClaimOwner`, `updateSubClaimName`
