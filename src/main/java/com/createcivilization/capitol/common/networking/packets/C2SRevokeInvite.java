package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// Cancels a still-pending invite the team sent to {@code target}.
public record C2SRevokeInvite(UUID target) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SRevokeInvite> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_revoke_invite"));

	public static final StreamCodec<ByteBuf, C2SRevokeInvite> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		payload -> payload.target().toString(),
		target -> new C2SRevokeInvite(UUID.fromString(target))
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
