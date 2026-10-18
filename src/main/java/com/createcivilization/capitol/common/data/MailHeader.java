package com.createcivilization.capitol.common.data;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// One row of an inbox/sent list; the body is fetched separately.
public record MailHeader(long id, @Nullable UUID senderUuid, @Nullable UUID senderTeamId,
						 String subject, long sentAt, boolean read, boolean external) {}
