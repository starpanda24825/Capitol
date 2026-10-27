package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Asks the server to resend the team directory (members, roles, invites)
public record C2SRequestTeamDirectory() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SRequestTeamDirectory> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_request_team_directory"));

	public static final StreamCodec<ByteBuf, C2SRequestTeamDirectory> STREAM_CODEC = StreamCodec.unit(new C2SRequestTeamDirectory());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
