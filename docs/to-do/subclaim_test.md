# Sub-claim test matrix
Manual checks for sub-claim ownership and the directory. Run with an owner, a member with and without `MANAGE_SUB_CLAIMS_OTHERS`, an outsider, and a second team.

## Protection semantics
- [ ] A non-member is denied inside a sub-claim even when the surrounding team would allow guests.
- [ ] A team member is allowed exactly the sub-claim's stored member bits, not the team role bits.
- [ ] The sub-claim owner always has full access.
- [ ] New sub-claims default to the baseline member permissions (not everything).
- [ ] A member without `CLAIM_SUB_CLAIMS` cannot create a sub-claim; the cap (`maxSubClaimsPerPlayer`) is enforced.

## Ownership
- [ ] Transfer requires the target to be a current team member and refuses self-transfer.
- [ ] Transferring on a name collision renames to `name(n)`.
- [ ] Rename is refused on a duplicate name owned by the same player and on names over 32 chars.
- [ ] Delete is allowed for the owner, the team owner, or a `MANAGE_SUB_CLAIMS_OTHERS` holder; deletion notifies the owner and removes the client border.
- [ ] Transfer/delete notify via system mail.

## Directory
- [ ] `list` shows only your sub-claims; `list all` requires `VIEW_ALL_SUB_CLAIMS` (or team owner/op) and is otherwise scoped with a notice.
- [ ] Paging/filter queries stay bounded (LIMIT/OFFSET) and indexed.
- [ ] A member leaving or being kicked has their sub-claims transferred to the leader.
