package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Starts a disband. When the team has no approvers the GUI must send the typed
 * team name in confirmName; an empty name just asks the server whether a
 * confirmation is needed.
 */
public record C2SRequestDisband(String confirmName) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SRequestDisband> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_request_disband"));

	public static final StreamCodec<ByteBuf, C2SRequestDisband> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		C2SRequestDisband::confirmName,
		C2SRequestDisband::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
