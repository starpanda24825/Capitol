package com.createcivilization.capitol.common.data;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record MailBody(long id, @Nullable UUID senderUuid, @Nullable UUID senderTeamId,
					   String subject, String body, long sentAt, boolean external) {}
