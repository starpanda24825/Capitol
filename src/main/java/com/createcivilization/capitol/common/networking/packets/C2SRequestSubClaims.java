package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Asks for a page of the tam's sub-claim directory. {@code all} is honoured only
 * wth {@code VIEW_ALL_SUB_CLAIMS}; {@code ownerFilter} is an optional UUID string.
 */

public record C2SRequestSubClaims(int page, boolean all, String ownerFilter, String query) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SRequestSubClaims> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_request_sub_claims"));

	public static final StreamCodec<ByteBuf, C2SRequestSubClaims> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		C2SRequestSubClaims::page,
		ByteBufCodecs.BOOL,
		C2SRequestSubClaims::all,
		ByteBufCodecs.STRING_UTF8,
		C2SRequestSubClaims::ownerFilter,
		ByteBufCodecs.STRING_UTF8,
		C2SRequestSubClaims::query,
		C2SRequestSubClaims::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
