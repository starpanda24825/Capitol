package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// Full protection bitfield for a sub-claim. 
public record C2SSetSubClaimProtections(UUID id, long protections) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SSetSubClaimProtections> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_set_sub_claim_protections"));

	public static final StreamCodec<ByteBuf, C2SSetSubClaimProtections> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		value -> value.id().toString(),
		ByteBufCodecs.VAR_LONG,
		C2SSetSubClaimProtections::protections,
		(id, protections) -> new C2SSetSubClaimProtections(UUID.fromString(id), protections)
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
