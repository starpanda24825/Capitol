# Networking
Chunk claim data is synced between server and client via NeoForge's networking layer. `CapitolNetworking` registers all packet types and routes them to their handlers.

### Packets
- **S2CChunkData** — server tells client a chunk is claimed (sends ChunkPos + Team data)
- **S2CChunkRemove** — server tells client a chunk was unclaimed
- **C2SChunkRequest** — client asks server for a chunk's ownership info
- **S2CSubClaimData** — server tells client a sub-claim exists (id, dimension, min/max XYZ); on a dedicated server, sent to a player on join for every sub-claim, and to players tracking the centre chunk on creation
- **S2CSubClaimRemove** — server tells client a sub-claim was removed (delete command, or an unclaim that intersects it)
- **C2SCreateSubClaim** — client submits a new sub-claim's name, dimension and box after the naming screen

### Book / mail packets (registrar `5`)
- **Role/member** — `C2SCreateRole`, `C2SEditRole`, `C2SSetRolePermissions`, `C2SDeleteRole`, `C2SMoveRole`, `C2SAssignRole`, `C2SKickMember`; the server answers any of them with `S2CActionResult(success, langKey, args)`.
- **Lifecycle** — `C2STransferLeadership`, `C2SLeaveTeam`, `C2SRequestDisband`, `C2SRespondDisband`, `C2SCancelDisband`.
- **Mail** — `C2SSendMail`, `C2SRequestInbox`, `C2SOpenMail`, `C2SMailAction` up; `S2CInboxPage`, `S2CMailBody`, `S2CMailUnread` down.
- **Team directory** — `S2CTeamDirectory` is one read-only snapshot per team (members, roles, pending invites, capability flags) so the team screens need no per-tab round trips; `C2SRequestTeamDirectory` asks for it and `C2SRevokeInvite` cancels a pending invite.
- **Team editing** — `C2SEditTeam` saves the team's name/tag/description/colour; the server re-validates every field and `MANAGE_TEAM`.
- **Manager book** — `C2SOpenBook` up, `S2COpenManagerBook` down (the viewer's team + effective permissions), opened by `/capitol menu` or the keybind; the sections read the shared snapshot below.

### Rate limiting
Read requests (`C2SOpenBook`, `C2SRequestInbox`, `C2SRequestSubClaims`, `C2SRequestTeamDirectory`) pass through `RequestThrottle`, a per-player limit of 5/second, so a spammed request can't turn into packet spam.

### Client Side
- `ClientClaimCache` stores a `HashMap<ChunkPos, Team>` of all known claims
- `ClientSubClaimCache` stores a `Map<UUID, SubClaimRenderData>` of synced sub-claim boxes for rendering
- `ChunkEvents` keeps the caches in sync: sends a `C2SChunkRequest` on chunk load, removes from cache on chunk unload, clears everything (both caches) on disconnect
- `ClientPayloadHandler` receives S2C packets and updates the caches
- `ServerPayloadHandler` receives C2S requests and responds with the chunk's owner from the database; also validates and persists `C2SCreateSubClaim` and damages the wand on `C2SDamageWand`
- `ClientMailCache` mirrors the last inbox page, open mail and unread count; cleared on logout with the other caches
- `ClientTeamDirectoryCache` mirrors the last `S2CTeamDirectory` snapshot; cleared on logout with the other caches
