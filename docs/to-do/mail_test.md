# Mail test matrix
Manual checks for the mail backend. Ran with an owner, a plain member, a role with "mailable" off, and a second team with and without `RECEIVE_EXTERNAL_MAIL` holders.

## Permissions
- [ ] Each target kind (members/roles/team-wide/external) is refused without its send permission.
- [ ] A role with the mailable flag off is refused as a group target.
- [ ] External mail is refused when disabled, the target team is missing, or it has no external-mail holders.
- [ ] System mail (invite, transfer, disband, role assign/remove) arrives with no sender.

## Limits
- [ ] Subject/body are trimmed, control chars stripped, and length capped.
- [ ] More than `maxRecipients` is refused.
- [ ] Sending to a full inbox is refused (oldest read first is not silently dropped).
- [ ] Send and external cooldowns are enforced per player.
- [ ] `purgeMailOlderThan` removes only mail past `retentionDays` on server start.

## Views
- [ ] Inbox lists headers only; opening a mail fetches the body and clears its unread flag.
- [ ] Delete removes the mail for that player only.
- [ ] Unread counts push on login and update on send/read/delete.
- [ ] Recipients keep mail after the sending team disbands.
