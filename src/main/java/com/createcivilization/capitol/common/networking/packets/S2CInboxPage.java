package com.createcivilization.capitol.common.networking.packets;

import com.createcivilization.capitol.common.data.MailSummary;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2CInboxPage(String box, int page, int unread, List<MailSummary> mails) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<S2CInboxPage> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "s2c_inbox_page"));

	public static final StreamCodec<ByteBuf, S2CInboxPage> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2CInboxPage decode(ByteBuf buffer) {
			String box = ByteBufCodecs.STRING_UTF8.decode(buffer);
			int page = ByteBufCodecs.VAR_INT.decode(buffer);
			int unread = ByteBufCodecs.VAR_INT.decode(buffer);
			int size = ByteBufCodecs.VAR_INT.decode(buffer);
			List<MailSummary> mails = new ArrayList<>(size);
			for (int i = 0; i < size; i++) mails.add(MailSummary.STREAM_CODEC.decode(buffer));
			return new S2CInboxPage(box, page, unread, mails);
		}

		@Override
		public void encode(ByteBuf buffer, S2CInboxPage value) {
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.box());
			ByteBufCodecs.VAR_INT.encode(buffer, value.page());
			ByteBufCodecs.VAR_INT.encode(buffer, value.unread());
			ByteBufCodecs.VAR_INT.encode(buffer, value.mails().size());
			for (MailSummary mail : value.mails()) MailSummary.STREAM_CODEC.encode(buffer, mail);
		}
	};

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
