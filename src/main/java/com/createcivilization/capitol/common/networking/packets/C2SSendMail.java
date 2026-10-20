package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A compose request. Which fields matter depends on {@code targetKind}:
 * MEMBERS/Roles use the lists, EXTERNAL uses the team name.
 */
public record C2SSendMail(String targetKind, List<UUID> recipients, List<Integer> roleIds,
						  String externalTeamName, String subject, String body) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SSendMail> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_send_mail"));

	public static final StreamCodec<ByteBuf, C2SSendMail> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public C2SSendMail decode(ByteBuf buffer) {
			String targetKind = ByteBufCodecs.STRING_UTF8.decode(buffer);
			int recipientCount = ByteBufCodecs.VAR_INT.decode(buffer);
			List<UUID> recipients = new ArrayList<>(recipientCount);
			for (int i = 0; i < recipientCount; i++) recipients.add(UUID.fromString(ByteBufCodecs.STRING_UTF8.decode(buffer)));
			int roleCount = ByteBufCodecs.VAR_INT.decode(buffer);
			List<Integer> roleIds = new ArrayList<>(roleCount);
			for (int i = 0; i < roleCount; i++) roleIds.add(ByteBufCodecs.VAR_INT.decode(buffer));
			String externalTeamName = ByteBufCodecs.STRING_UTF8.decode(buffer);
			String subject = ByteBufCodecs.STRING_UTF8.decode(buffer);
			String body = ByteBufCodecs.STRING_UTF8.decode(buffer);
			return new C2SSendMail(targetKind, recipients, roleIds, externalTeamName, subject, body);
		}

		@Override
		public void encode(ByteBuf buffer, C2SSendMail value) {
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.targetKind());
			ByteBufCodecs.VAR_INT.encode(buffer, value.recipients().size());
			for (UUID recipient : value.recipients()) ByteBufCodecs.STRING_UTF8.encode(buffer, recipient.toString());
			ByteBufCodecs.VAR_INT.encode(buffer, value.roleIds().size());
			for (int roleId : value.roleIds()) ByteBufCodecs.VAR_INT.encode(buffer, roleId);
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.externalTeamName());
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.subject());
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.body());
		}
	};

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
