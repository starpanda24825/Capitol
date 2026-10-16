# Networking
Chunk claim data is synced between server and client via NeoForge's networking layer. `CapitolNetworking` registers all packet types and routes them to their handlers.

### Packets
- **S2CChunkData** — server tells client a chunk is claimed (sends ChunkPos + Team data)
- **S2CChunkRemove** — server tells client a chunk was unclaimed
- **C2SChunkRequest** — client asks server for a chunk's ownership info
- **S2CSubClaimData** — server tells client a sub-claim exists (id, dimension, min/max XYZ); on a dedicated server, sent to a player on join for every sub-claim, and to players tracking the centre chunk on creation
- **S2CSubClaimRemove** — server tells client a sub-claim was removed (delete command, or an unclaim that intersects it)
- **C2SCreateSubClaim** — client submits a new sub-claim's name, dimension and box after the naming screen

### Client Side
- `ClientClaimCache` stores a `HashMap<ChunkPos, Team>` of all known claims
- `ClientSubClaimCache` stores a `Map<UUID, SubClaimRenderData>` of synced sub-claim boxes for rendering
- `ChunkEvents` keeps the caches in sync: sends a `C2SChunkRequest` on chunk load, removes from cache on chunk unload, clears everything (both caches) on disconnect
- `ClientPayloadHandler` receives S2C packets and updates the caches
- `ServerPayloadHandler` receives C2S requests and responds with the chunk's owner from the database; also validates and persists `C2SCreateSubClaim` and damages the wand on `C2SDamageWand`