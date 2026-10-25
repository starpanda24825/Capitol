# Mayor testing
Manual checks for the mayor-scoped authority. A mayor runs the chunks that belong to their capitol block (`chunks.capitol_block_id`).

## Scoped permissions
- [ ] A mayor can claim/unclaim inside their own block's area without the global `CLAIM_CHUNKS`/`UNCLAIM_CHUNKS`.
- [ ] Outside the block's radius the mayor falls back to their normal team permissions.
- [ ] Only `CLAIM_CHUNKS`, `UNCLAIM_CHUNKS`, `MANAGE_SUB_CLAIMS_OTHERS`, `VIEW_ALL_SUB_CLAIMS` are honoured from the mayor role (`RoleRules.MAYOR_ALLOWED_MASK`).
- [ ] Chunks a mayor claims are stored with their block's id; `/capitol claim chunk` still needs the global permission.
- [ ] With `enforceCapitolTierClaimLimits` on, a mayor cannot exceed the block's tier `maxClaims`; off by default.

## Lifecycle
- [ ] A mayor who leaves or is kicked has their blocks reset to the team leader.
- [ ] Leadership transfer makes the new leader the Capital's mayor.
- [ ] Destroying a block clears its mayor and the cached mayoral list.
- [ ] One player can be mayor of several blocks.
- [ ] Assigning/clearing a mayor needs `ASSIGN_MAYORS` (not `MANAGE_CAPITOL_BLOCKS`) and a target in the team; assigning sends system mail.
- [ ] The book shows claims used/max and a mayor hint; no SQL on the protection hot path beyond the cached mayoral lookup.
