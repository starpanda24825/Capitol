package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record S2CActionResult(boolean success, @Nullable String langKey, List<String> args) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<S2CActionResult> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "s2c_action_result"));

	public static final StreamCodec<ByteBuf, S2CActionResult> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2CActionResult decode(ByteBuf buffer) {
			boolean success = ByteBufCodecs.BOOL.decode(buffer);
			String langKey = ByteBufCodecs.BOOL.decode(buffer) ? ByteBufCodecs.STRING_UTF8.decode(buffer) : null;
			int size = ByteBufCodecs.VAR_INT.decode(buffer);
			List<String> args = new ArrayList<>(size);
			for (int i = 0; i < size; i++) args.add(ByteBufCodecs.STRING_UTF8.decode(buffer));
			return new S2CActionResult(success, langKey, args);
		}

		@Override
		public void encode(ByteBuf buffer, S2CActionResult value) {
			ByteBufCodecs.BOOL.encode(buffer, value.success());
			ByteBufCodecs.BOOL.encode(buffer, value.langKey() != null);
			if (value.langKey() != null) ByteBufCodecs.STRING_UTF8.encode(buffer, value.langKey());
			ByteBufCodecs.VAR_INT.encode(buffer, value.args().size());
			for (String arg : value.args()) ByteBufCodecs.STRING_UTF8.encode(buffer, arg);
		}
	};

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
