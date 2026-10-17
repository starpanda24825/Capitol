package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record C2STransferLeadership(UUID target) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2STransferLeadership> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_transfer_leadership"));

	public static final StreamCodec<ByteBuf, C2STransferLeadership> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		payload -> payload.target().toString(),
		target -> new C2STransferLeadership(UUID.fromString(target))
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
