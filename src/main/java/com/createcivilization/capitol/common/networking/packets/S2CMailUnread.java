package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CMailUnread(int count) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<S2CMailUnread> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "s2c_mail_unread"));

	public static final StreamCodec<ByteBuf, S2CMailUnread> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		S2CMailUnread::count,
		S2CMailUnread::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
