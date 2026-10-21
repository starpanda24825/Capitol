package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// Full member permission bitfield for a sub-claim.
public record C2SSetSubClaimPermissions(UUID id, long permissions) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SSetSubClaimPermissions> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_set_sub_claim_permissions"));

	public static final StreamCodec<ByteBuf, C2SSetSubClaimPermissions> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		value -> value.id().toString(),
		ByteBufCodecs.VAR_LONG,
		C2SSetSubClaimPermissions::permissions,
		(id, permissions) -> new C2SSetSubClaimPermissions(UUID.fromString(id), permissions)
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
