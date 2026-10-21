package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record C2SDeleteSubClaim(UUID id) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SDeleteSubClaim> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_delete_sub_claim"));

	public static final StreamCodec<ByteBuf, C2SDeleteSubClaim> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		value -> value.id().toString(),
		id -> new C2SDeleteSubClaim(UUID.fromString(id))
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
