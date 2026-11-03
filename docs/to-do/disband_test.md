# Disband & leadership test
Manual checks for team lifecycle: disband requests, approval, the protection lock, and leadership transfer/leave. Run with an owner, two `APPROVE_DISBAND` holders and a plain member, then kill/restart the server mid-flow.

## Disband
- [ ] A `DISBAND_TEAM` holder starts a request; every `APPROVE_DISBAND` holder except the requester must approve.
- [ ] Any deny cancels the request; an empty approver set falls back to the typed-name `confirm_disband` path.
- [ ] The request expires after `disbandApprovalHours` and survives a restart.
- [ ] Changing the approver set (role perms, assign/unassign, kick/leave of a holder) cancels the request and starts a `disbandLockHours` lock.
- [ ] `MANAGE_TEAM` alone does **not** start a disband.
- [ ] Ops can force a disband past the flow.
- [ ] `finalizeDisband` broadcasts chunk + sub-claim removals, un-forces forceloaded chunks, clears war takeover state and invites, then deletes the team last.
- [ ] After finalising, clients drop the team's chunks/sub-claims and ex-members get no stale permissions.

## Leadership
- [ ] The owner cannot leave without naming a new leader.
- [ ] Leaving as owner promotes the new leader and leaves in one transaction (kill the server mid-way: never zero or two owners).
- [ ] Transfer refuses an offline player, a non-member, or yourself.
- [ ] Transfer syncs the Capital's mayor, resets any pending disband request, and sends system mail.
- [ ] Kicking or leaving an `APPROVE_DISBAND` holder is treated as an approver-set change.
