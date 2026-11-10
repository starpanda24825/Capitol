package com.createcivilization.capitol.common.networking.packets;

import com.createcivilization.capitol.common.data.TeamSubTab;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One read-only snapshot of a team, used by the Members and Roles manager tabs so
 * neither tab needs its own round trips. Everything a page greys out (capabilities,
 * the viewer's hierarchy position) travels with the data; the server still
 * re-validates every action a page sends back.
 */
public record S2CTeamDirectory(List<MemberRow> members, List<RoleRow> roles, List<InviteRow> invites,
								List<TeamSubTab> subTabs,
								boolean canKick, boolean canAssignRoles, boolean canManageRoles, boolean canInvite,
								int highestPosition, long permissions) implements CustomPacketPayload {

	// A role chip shown next to a member; system chips get a lock in the UI.
	public record RoleChip(int id, String name, int color, int position, boolean system) {

		public static final StreamCodec<ByteBuf, RoleChip> STREAM_CODEC = new StreamCodec<>() {
			@Override
			public RoleChip decode(ByteBuf buffer) {
				return new RoleChip(
					ByteBufCodecs.VAR_INT.decode(buffer),
					ByteBufCodecs.STRING_UTF8.decode(buffer),
					ByteBufCodecs.INT.decode(buffer),
					ByteBufCodecs.VAR_INT.decode(buffer),
					ByteBufCodecs.BOOL.decode(buffer));
			}

			@Override
			public void encode(ByteBuf buffer, RoleChip value) {
				ByteBufCodecs.VAR_INT.encode(buffer, value.id());
				ByteBufCodecs.STRING_UTF8.encode(buffer, value.name());
				ByteBufCodecs.INT.encode(buffer, value.color());
				ByteBufCodecs.VAR_INT.encode(buffer, value.position());
				ByteBufCodecs.BOOL.encode(buffer, value.system());
			}
		};
	}

	// A member with every role they hold and whether the viewer may touch them
	public record MemberRow(UUID uuid, String name, boolean owner, boolean online, long individualPermissions,
							List<RoleChip> roles) {

		public static final StreamCodec<ByteBuf, MemberRow> STREAM_CODEC = new StreamCodec<>() {
			@Override
			public MemberRow decode(ByteBuf buffer) {
				UUID uuid = UUID.fromString(ByteBufCodecs.STRING_UTF8.decode(buffer));
				String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
				boolean owner = ByteBufCodecs.BOOL.decode(buffer);
				boolean online = ByteBufCodecs.BOOL.decode(buffer);
				long individual = ByteBufCodecs.VAR_LONG.decode(buffer);
				List<RoleChip> roles = decodeChips(buffer);
				return new MemberRow(uuid, name, owner, online, individual, roles);
			}

			@Override
			public void encode(ByteBuf buffer, MemberRow value) {
				ByteBufCodecs.STRING_UTF8.encode(buffer, value.uuid().toString());
				ByteBufCodecs.STRING_UTF8.encode(buffer, value.name());
				ByteBufCodecs.BOOL.encode(buffer, value.owner());
				ByteBufCodecs.BOOL.encode(buffer, value.online());
				ByteBufCodecs.VAR_LONG.encode(buffer, value.individualPermissions());
				encodeChips(buffer, value.roles());
			}
		};
	}

	// A full role entry, for both the member chips and the Roles tab editor.
	public record RoleRow(int id, String name, int color, int position, String systemKey, boolean mailable,
						  long permissions, int memberCount) {

		public static final StreamCodec<ByteBuf, RoleRow> STREAM_CODEC = new StreamCodec<>() {
			@Override
			public RoleRow decode(ByteBuf buffer) {
				int id = ByteBufCodecs.VAR_INT.decode(buffer);
				String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
				int color = ByteBufCodecs.INT.decode(buffer);
				int position = ByteBufCodecs.VAR_INT.decode(buffer);
				boolean hasKey = ByteBufCodecs.BOOL.decode(buffer);
				String systemKey = hasKey ? ByteBufCodecs.STRING_UTF8.decode(buffer) : null;
				boolean mailable = ByteBufCodecs.BOOL.decode(buffer);
				long permissions = ByteBufCodecs.VAR_LONG.decode(buffer);
				int memberCount = ByteBufCodecs.VAR_INT.decode(buffer);
				return new RoleRow(id, name, color, position, systemKey, mailable, permissions, memberCount);
			}

			@Override
			public void encode(ByteBuf buffer, RoleRow value) {
				ByteBufCodecs.VAR_INT.encode(buffer, value.id());
				ByteBufCodecs.STRING_UTF8.encode(buffer, value.name());
				ByteBufCodecs.INT.encode(buffer, value.color());
				ByteBufCodecs.VAR_INT.encode(buffer, value.position());
				ByteBufCodecs.BOOL.encode(buffer, value.systemKey() != null);
				if (value.systemKey() != null) ByteBufCodecs.STRING_UTF8.encode(buffer, value.systemKey());
				ByteBufCodecs.BOOL.encode(buffer, value.mailable());
				ByteBufCodecs.VAR_LONG.encode(buffer, value.permissions());
				ByteBufCodecs.VAR_INT.encode(buffer, value.memberCount());
			}
		};
	}

	//An outstanding invite the team can revoke.
	public record InviteRow(UUID uuid, String name) {

		public static final StreamCodec<ByteBuf, InviteRow> STREAM_CODEC = new StreamCodec<>() {
			@Override
			public InviteRow decode(ByteBuf buffer) {
				return new InviteRow(
					UUID.fromString(ByteBufCodecs.STRING_UTF8.decode(buffer)),
					ByteBufCodecs.STRING_UTF8.decode(buffer));
			}

			@Override
			public void encode(ByteBuf buffer, InviteRow value) {
				ByteBufCodecs.STRING_UTF8.encode(buffer, value.uuid().toString());
				ByteBufCodecs.STRING_UTF8.encode(buffer, value.name());
			}
		};
	}

	private static List<RoleChip> decodeChips(ByteBuf buffer) {
		int size = ByteBufCodecs.VAR_INT.decode(buffer);
		List<RoleChip> chips = new ArrayList<>(size);
		for (int i = 0; i < size; i++) chips.add(RoleChip.STREAM_CODEC.decode(buffer));
		return chips;
	}

	private static void encodeChips(ByteBuf buffer, List<RoleChip> chips) {
		ByteBufCodecs.VAR_INT.encode(buffer, chips.size());
		for (RoleChip chip : chips) RoleChip.STREAM_CODEC.encode(buffer, chip);
	}

	public static final CustomPacketPayload.Type<S2CTeamDirectory> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "s2c_team_directory"));

	public static final StreamCodec<ByteBuf, S2CTeamDirectory> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2CTeamDirectory decode(ByteBuf buffer) {
			int memberCount = ByteBufCodecs.VAR_INT.decode(buffer);
			List<MemberRow> members = new ArrayList<>(memberCount);
			for (int i = 0; i < memberCount; i++) members.add(MemberRow.STREAM_CODEC.decode(buffer));

			int roleCount = ByteBufCodecs.VAR_INT.decode(buffer);
			List<RoleRow> roles = new ArrayList<>(roleCount);
			for (int i = 0; i < roleCount; i++) roles.add(RoleRow.STREAM_CODEC.decode(buffer));

			int inviteCount = ByteBufCodecs.VAR_INT.decode(buffer);
			List<InviteRow> invites = new ArrayList<>(inviteCount);
			for (int i = 0; i < inviteCount; i++) invites.add(InviteRow.STREAM_CODEC.decode(buffer));

			int tabCount = ByteBufCodecs.VAR_INT.decode(buffer);
			List<TeamSubTab> subTabs = new ArrayList<>(tabCount);
			for (int i = 0; i < tabCount; i++) subTabs.add(TeamSubTab.STREAM_CODEC.decode(buffer));

			boolean canKick = ByteBufCodecs.BOOL.decode(buffer);
			boolean canAssignRoles = ByteBufCodecs.BOOL.decode(buffer);
			boolean canManageRoles = ByteBufCodecs.BOOL.decode(buffer);
			boolean canInvite = ByteBufCodecs.BOOL.decode(buffer);
			int highestPosition = ByteBufCodecs.VAR_INT.decode(buffer);
			long permissions = ByteBufCodecs.VAR_LONG.decode(buffer);
			return new S2CTeamDirectory(members, roles, invites, subTabs, canKick, canAssignRoles, canManageRoles, canInvite, highestPosition, permissions);
		}

		@Override
		public void encode(ByteBuf buffer, S2CTeamDirectory value) {
			ByteBufCodecs.VAR_INT.encode(buffer, value.members().size());
			for (MemberRow row : value.members()) MemberRow.STREAM_CODEC.encode(buffer, row);

			ByteBufCodecs.VAR_INT.encode(buffer, value.roles().size());
			for (RoleRow row : value.roles()) RoleRow.STREAM_CODEC.encode(buffer, row);

			ByteBufCodecs.VAR_INT.encode(buffer, value.invites().size());
			for (InviteRow row : value.invites()) InviteRow.STREAM_CODEC.encode(buffer, row);

			ByteBufCodecs.VAR_INT.encode(buffer, value.subTabs().size());
			for (TeamSubTab tab : value.subTabs()) TeamSubTab.STREAM_CODEC.encode(buffer, tab);

			ByteBufCodecs.BOOL.encode(buffer, value.canKick());
			ByteBufCodecs.BOOL.encode(buffer, value.canAssignRoles());
			ByteBufCodecs.BOOL.encode(buffer, value.canManageRoles());
			ByteBufCodecs.BOOL.encode(buffer, value.canInvite());
			ByteBufCodecs.VAR_INT.encode(buffer, value.highestPosition());
			ByteBufCodecs.VAR_LONG.encode(buffer, value.permissions());
		}
	};

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
