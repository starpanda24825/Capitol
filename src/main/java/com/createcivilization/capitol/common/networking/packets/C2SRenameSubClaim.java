package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record C2SRenameSubClaim(UUID id, String name) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SRenameSubClaim> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_rename_sub_claim"));

	public static final StreamCodec<ByteBuf, C2SRenameSubClaim> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		value -> value.id().toString(),
		ByteBufCodecs.STRING_UTF8,
		C2SRenameSubClaim::name,
		(id, name) -> new C2SRenameSubClaim(UUID.fromString(id), name)
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
