# Teams
A `Team` represents a group of players that can claim and control territory. Built via `Team.builder()` with UUID, name, ARGB color, and creation timestamp.

- Each team has a list of `TeamMember` records (player UUID + base role); extra roles and individual permission overrides stack on top (see [roles.md](roles.md))
- Teams track their loaded chunks via `WeakReference<ChunkPos>` — auto-cleaned when chunks unload
- `StreamCodec` support for sending team data over the network
- `fromResultSet()` factory for deserializing from SQLite rows
- Color is stored as a single ARGB `int`, converted to `java.awt.Color` for rendering
- Teams own their members' [sub-claims](subclaims.md); when a member leaves their sub-claims transfer to the team leader
