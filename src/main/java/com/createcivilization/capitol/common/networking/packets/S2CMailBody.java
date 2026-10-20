package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record S2CMailBody(long id, @Nullable String senderName, @Nullable String teamName,
						  String subject, String body, long sentAt, boolean external) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<S2CMailBody> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "s2c_mail_body"));

	public static final StreamCodec<ByteBuf, S2CMailBody> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2CMailBody decode(ByteBuf buffer) {
			long id = ByteBufCodecs.VAR_LONG.decode(buffer);
			String senderName = ByteBufCodecs.BOOL.decode(buffer) ? ByteBufCodecs.STRING_UTF8.decode(buffer) : null;
			String teamName = ByteBufCodecs.BOOL.decode(buffer) ? ByteBufCodecs.STRING_UTF8.decode(buffer) : null;
			String subject = ByteBufCodecs.STRING_UTF8.decode(buffer);
			String body = ByteBufCodecs.STRING_UTF8.decode(buffer);
			long sentAt = ByteBufCodecs.VAR_LONG.decode(buffer);
			boolean external = ByteBufCodecs.BOOL.decode(buffer);
			return new S2CMailBody(id, senderName, teamName, subject, body, sentAt, external);
		}

		@Override
		public void encode(ByteBuf buffer, S2CMailBody value) {
			ByteBufCodecs.VAR_LONG.encode(buffer, value.id());
			ByteBufCodecs.BOOL.encode(buffer, value.senderName() != null);
			if (value.senderName() != null) ByteBufCodecs.STRING_UTF8.encode(buffer, value.senderName());
			ByteBufCodecs.BOOL.encode(buffer, value.teamName() != null);
			if (value.teamName() != null) ByteBufCodecs.STRING_UTF8.encode(buffer, value.teamName());
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.subject());
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.body());
			ByteBufCodecs.VAR_LONG.encode(buffer, value.sentAt());
			ByteBufCodecs.BOOL.encode(buffer, value.external());
		}
	};

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
