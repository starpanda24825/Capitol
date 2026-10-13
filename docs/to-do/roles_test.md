# Roles test matrix
Manual checks for the roles overhaul. Run in a test world with an owner and a member account plus a second team, and with a non-op account.

## Hierarchy
- [X] A member without `MANAGE_ROLES` cannot create/edit/delete roles.
- [X] A `MANAGE_ROLES` holder cannot edit/delete a role at or above their highest position.
- [X] A `KICK_MEMBERS` holder cannot kick a member at or above their rank, or themselves.
- [X] The owner role cannot be edited, deleted, assigned or renamed (ops included).
- [X] Ops bypass hierarchy/permission checks but not the owner/system invariants.
- [X] System roles (owner/member/default/mayor) cannot be deleted.

## Grant-what-you-hold
- [ ] Setting a role's permissions to something the actor doesn't hold is refused.
- [ ] Setting individual overrides above what the actor holds is refused.
- [X] Assigning a role whose permissions exceed the actor's own is refused.
- [ ] The default (guest) role silently masks off administrative permissions.
- [*] The mayor role only accepts `CLAIM_CHUNKS`, `UNCLAIM_CHUNKS`, `MANAGE_SUB_CLAIMS_OTHERS`, `VIEW_ALL_SUB_CLAIMS`.

## Names
- [X] Names are 1-24 chars using letters/digits/space/`_`/`-` only.
- [X] Reserved names (owner/member/default/mayor) are refused, case-insensitively.
- [X] Duplicate names within a team are refused case-insensitively.
- [X] Deleting a custom role frees its name for reuse.

## Caps and caches
- [ ] `maxRolesPerTeam` and `maxExtraRolesPerMember` are enforced.
- [ ] Editing a role's permissions clears the whole team's permission cache.
- [ ] Assign/unassign/kick clears only that player's cache entry.
- [X] Owner always resolves to the full current permission set.

## TODO
- [ ] fix issue with toggling roles on/off not working (false -> true works, true -> false doesn't)
- [ ] Add appropriate message for duplicate role names.