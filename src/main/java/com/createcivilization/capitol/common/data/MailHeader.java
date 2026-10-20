package com.createcivilization.capitol.common.data;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// One row of an inbox/sent list; the body is fetched separately. targetTeamId and recipients only matter for the sent box.
public record MailHeader(long id, @Nullable UUID senderUuid, @Nullable UUID senderTeamId,
						 @Nullable UUID targetTeamId, String subject, long sentAt, boolean read,
						 boolean external, int recipients) {}
