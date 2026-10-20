package com.createcivilization.capitol.common.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/*
 * One list row of an inbox or sent box. The body is fetched separately so a
 * page stays small; names are resolved server-side.
 */
public record MailSummary(long id, @Nullable String senderName, @Nullable String teamName,
						  String subject, long sentAt, boolean read, boolean external, int recipients) {

	public static final StreamCodec<ByteBuf, MailSummary> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public MailSummary decode(ByteBuf buffer) {
			long id = ByteBufCodecs.VAR_LONG.decode(buffer);
			String senderName = ByteBufCodecs.BOOL.decode(buffer) ? ByteBufCodecs.STRING_UTF8.decode(buffer) : null;
			String teamName = ByteBufCodecs.BOOL.decode(buffer) ? ByteBufCodecs.STRING_UTF8.decode(buffer) : null;
			String subject = ByteBufCodecs.STRING_UTF8.decode(buffer);
			long sentAt = ByteBufCodecs.VAR_LONG.decode(buffer);
			boolean read = ByteBufCodecs.BOOL.decode(buffer);
			boolean external = ByteBufCodecs.BOOL.decode(buffer);
			int recipients = ByteBufCodecs.VAR_INT.decode(buffer);
			return new MailSummary(id, senderName, teamName, subject, sentAt, read, external, recipients);
		}

		@Override
		public void encode(ByteBuf buffer, MailSummary value) {
			ByteBufCodecs.VAR_LONG.encode(buffer, value.id());
			encodeOptional(buffer, value.senderName());
			encodeOptional(buffer, value.teamName());
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.subject());
			ByteBufCodecs.VAR_LONG.encode(buffer, value.sentAt());
			ByteBufCodecs.BOOL.encode(buffer, value.read());
			ByteBufCodecs.BOOL.encode(buffer, value.external());
			ByteBufCodecs.VAR_INT.encode(buffer, value.recipients());
		}
	};

	// Nullable strings are length-prefixed by the string codec itself, so a presence flag is enough.
	private static void encodeOptional(ByteBuf buffer, @Nullable String value) {
		ByteBufCodecs.BOOL.encode(buffer, value != null);
		if (value != null) ByteBufCodecs.STRING_UTF8.encode(buffer, value);
	}
}
