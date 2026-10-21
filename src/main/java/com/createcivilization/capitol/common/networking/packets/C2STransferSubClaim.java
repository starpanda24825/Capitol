package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// Hand a sub-claim over to another team member.
public record C2STransferSubClaim(UUID id, UUID newOwner) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2STransferSubClaim> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_transfer_sub_claim"));

	public static final StreamCodec<ByteBuf, C2STransferSubClaim> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		value -> value.id().toString(),
		ByteBufCodecs.STRING_UTF8,
		value -> value.newOwner().toString(),
		(id, owner) -> new C2STransferSubClaim(UUID.fromString(id), UUID.fromString(owner))
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
