package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Applies a mail action; {@code action} is "READ" or "DELETE".
public record C2SMailAction(long id, String action) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SMailAction> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_mail_action"));

	public static final StreamCodec<ByteBuf, C2SMailAction> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG,
		C2SMailAction::id,
		ByteBufCodecs.STRING_UTF8,
		C2SMailAction::action,
		C2SMailAction::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
