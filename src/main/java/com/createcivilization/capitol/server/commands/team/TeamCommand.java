package com.createcivilization.capitol.server.commands.team;

import com.createcivilization.capitol.common.data.*;
import com.createcivilization.capitol.common.managers.DatabaseManager;
import com.createcivilization.capitol.common.managers.RoleManager;
import com.createcivilization.capitol.common.managers.TeamLifecycleManager;
import com.createcivilization.capitol.common.modules.database.CapitolDatabase;
import com.createcivilization.capitol.common.networking.packets.S2CChunkRemove;
import com.createcivilization.capitol.server.invites.InviteHandler;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TeamCommand {

	private static final Map<String, String> NAMED_COLORS = new LinkedHashMap<>();
	static {
		NAMED_COLORS.put("red",     "FF0000");
		NAMED_COLORS.put("blue",    "0000FF");
		NAMED_COLORS.put("green",   "008000");
		NAMED_COLORS.put("lime",    "00FF00");
		NAMED_COLORS.put("yellow",  "FFFF00");
		NAMED_COLORS.put("orange",  "FFA500");
		NAMED_COLORS.put("purple",  "800080");
		NAMED_COLORS.put("pink",    "FFC0CB");
		NAMED_COLORS.put("cyan",    "00FFFF");
		NAMED_COLORS.put("white",   "FFFFFF");
		NAMED_COLORS.put("black",   "000000");
		NAMED_COLORS.put("gray",    "808080");
		NAMED_COLORS.put("navy",    "000080");
		NAMED_COLORS.put("teal",    "008080");
		NAMED_COLORS.put("maroon",  "800000");
		NAMED_COLORS.put("gold",    "FFD700");
	}

	public static LiteralArgumentBuilder<CommandSourceStack> register() {
		return Commands.literal("team")
			.then(Commands.literal("create")
				.then(Commands.argument("name", StringArgumentType.string())
					.then(Commands.argument("tag", StringArgumentType.word())
						.then(Commands.argument("color", StringArgumentType.word())
							.suggests((context, builder) -> {
								NAMED_COLORS.keySet().forEach(builder::suggest);
								return builder.buildFuture();
							})
							.then(Commands.argument("description", StringArgumentType.string())
								.executes(TeamCommand::createTeam))
							.executes(TeamCommand::createTeam)))))
			.then(Commands.literal("info").executes(TeamCommand::teamInfo))
			.then(Commands.literal("invite")
				.then(Commands.argument("player", EntityArgument.player())
					.executes(TeamCommand::invitePlayer)))
			.then(Commands.literal("kick")
				.then(Commands.argument("player", StringArgumentType.string())
					.suggests((context, builder) -> {
						CapitolDatabase database = DatabaseManager.database;
						Team team = database.getPlayerTeam(context.getSource().getPlayer());
						if (team == null) {
							builder.suggest("YOU ARE NOT IN A TEAM");
							return builder.buildFuture();
						}
						List<TeamMember> members = database.getTeamMembers(team);
						GameProfileCache profileCache = context.getSource().getServer().getProfileCache();
						for (TeamMember member : members) {
							profileCache.get(member.playerUUID()).ifPresent(
								gameProfile -> builder.suggest(gameProfile.getName())
							);
						}
						return builder.buildFuture();
					})
					.executes(TeamCommand::kickPlayer)))
			.then(Commands.literal("transfer")
				.then(Commands.argument("player", StringArgumentType.string())
					.suggests(TeamCommand::suggestMembers)
					.executes(TeamCommand::transferLeadership)))
			.then(Commands.literal("leave")
				.then(Commands.argument("new_leader", StringArgumentType.string())
					.suggests(TeamCommand::suggestMembers)
					.executes(TeamCommand::leaveTeamWithLeader))
				.executes(TeamCommand::leaveTeam))
			.then(Commands.literal("disband")
				.then(Commands.literal("approve").executes(context -> respondDisband(context, true)))
				.then(Commands.literal("deny").executes(context -> respondDisband(context, false)))
				.then(Commands.literal("cancel").executes(TeamCommand::cancelDisband))
				.then(Commands.literal("status").executes(TeamCommand::disbandStatus))
				.executes(TeamCommand::promptDisband))
			.then(Commands.literal("confirm_disband")
				.then(Commands.argument("team_name", StringArgumentType.string())
					.executes(TeamCommand::confirmDisband)))
			.then(TeamRoleCommand.register())
			.then(TeamProtectionCommand.register())
			.then(TeamPlayerCommand.register())
			.then(TeamForceloadCommand.register());
	}

	private static int invitePlayer(CommandContext<CommandSourceStack> context) {
		CapitolDatabase database = DatabaseManager.database;
		Player player = context.getSource().getPlayer();

		Team team = database.getPlayerTeam(player);
		if (team == null) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.not_in_team_error").withStyle(ChatFormatting.RED));
			return 0;
		}

		if (!Permission.INVITE_MEMBERS.hasPermission(database.getPlayerPermission(player, team))) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.team.invite.no_permission").withStyle(ChatFormatting.RED));
			return 0;
		}

		ServerPlayer playerToInvite;
		try {
			playerToInvite = EntityArgument.getPlayer(context, "player");
		} catch (CommandSyntaxException e) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.team.invite.invalid_player").withStyle(ChatFormatting.RED));
			return 0;
		}

		if (database.getPlayerTeam(playerToInvite) != null) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.team.invite.target_in_team").withStyle(ChatFormatting.RED));
			return 0;
		}

		InviteHandler.addInvite(playerToInvite, team);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.invite.success",
			Component.literal(playerToInvite.getName().getString()).withStyle(ChatFormatting.WHITE))
			.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int teamInfo(CommandContext<CommandSourceStack> context) {
		CapitolDatabase database = DatabaseManager.database;
		Player player = context.getSource().getPlayer();

		Team team = database.getPlayerTeam(player);
		if (team == null) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.not_in_team_error").withStyle(ChatFormatting.RED));
			return 0;
		}

		int rgb = team.getColor().getRGB() & 0xFFFFFF;
		String hex = String.format("#%06X", rgb);
		String claims = team.getMaxClaims() > 0
			? team.getCurrentClaims() + " / " + team.getMaxClaims()
			: String.valueOf(team.getCurrentClaims());
		String description = team.getDescription() != null && !team.getDescription().isEmpty()
			? team.getDescription()
			: "None";

		MutableComponent msg = Component.empty()
			.append(Component.literal("[" + team.getTag() + "] ").withStyle(ChatFormatting.AQUA))
			.append(Component.literal(team.getName()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
			.append(Component.literal("\n"))
			.append(Component.translatable("commands.capitol.team.info.color").withStyle(ChatFormatting.GRAY))
			.append(Component.literal("█ ").withStyle(s -> s.withColor(rgb)))
			.append(Component.literal(hex).withStyle(ChatFormatting.WHITE))
			.append(Component.literal("\n"))
			.append(Component.translatable("commands.capitol.team.info.description").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(description).withStyle(ChatFormatting.WHITE))
			.append(Component.literal("\n"))
			.append(Component.translatable("commands.capitol.team.info.claims").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(claims).withStyle(ChatFormatting.WHITE));

		context.getSource().sendSuccess(() -> msg, false);
		return 1;
	}

	private static int createTeam(CommandContext<CommandSourceStack> context) {
		CapitolDatabase database = DatabaseManager.database;
		Player player = context.getSource().getPlayer();

		if (database.getPlayerTeam(player) != null) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.team.create.already_in_team").withStyle(ChatFormatting.RED));
			return 0;
		}

		String name = StringArgumentType.getString(context, "name");

		if (!Team.isValidName(name)) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.team.create.invalid_name").withStyle(ChatFormatting.RED));
			return 0;
		}

		if (database.teamNameExists(name)) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.team.create.name_exists").withStyle(ChatFormatting.RED));
			return 0;
		}

		String hex = NAMED_COLORS.getOrDefault(
			StringArgumentType.getString(context, "color").toLowerCase(),
			StringArgumentType.getString(context, "color")
		).replace("#", "");
		String tag = StringArgumentType.getString(context, "tag");

		if (!Team.isValidTag(tag)) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.team.create.invalid_tag").withStyle(ChatFormatting.RED));
			return 0;
		}

		String description;
		try {
			description = StringArgumentType.getString(context, "description");
		} catch (IllegalArgumentException e) {
			description = "";
		}

		Color color;
		try {
			color = new Color((int) Long.parseLong(hex, 16), true);
		} catch (NumberFormatException e) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.team.create.invalid_color", hex).withStyle(ChatFormatting.RED));
			return 0;
		}

		Team team = Team.builder().name(name).id(UUID.randomUUID()).color(color).description(description).tag(tag).build();
		database.addTeam(team);
		TeamRole ownerRole = database.getRoleByName(team, TeamRole.OWNER_ROLE_NAME);
		database.addPlayerToTeam(player, team, ownerRole);
		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.create.success",
			Component.literal(name).withStyle(ChatFormatting.GOLD))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int kickPlayer(CommandContext<CommandSourceStack> context) {
		CapitolDatabase database = DatabaseManager.database;
		Player player = context.getSource().getPlayer();
		Team team = database.getPlayerTeam(player);
		String playerName = StringArgumentType.getString(context, "player");

		if (team == null) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.not_in_team_error").withStyle(ChatFormatting.RED));
			return 0;
		}

		GameProfileCache profileCache = context.getSource().getServer().getProfileCache();
		Optional<GameProfile> profile = profileCache.get(playerName);
		if (profile.isEmpty()) {
			context.getSource().sendFailure(Component.translatable("commands.capitol.no_player_found", playerName).withStyle(ChatFormatting.RED));
			return 0;
		}

		GameProfile gameProfile = profile.get();

		// hierarchy and permission checks are all in RoleManager
		RoleManager.Check result = RoleManager.kick(player, team, gameProfile.getId());
		if (!result.ok()) return TeamRoleCommand.failCommand(context, result.langKey(), result.args());

		InviteHandler.clearInvites(gameProfile.getId());

		ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(gameProfile.getId());
		if (online != null) {
			online.sendSystemMessage(Component.translatable("commands.capitol.team.kick.notify",
				Component.literal(team.getName()).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.RED));
		}

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.kick.success",
			Component.literal(gameProfile.getName()).withStyle(ChatFormatting.WHITE))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int leaveTeam(CommandContext<CommandSourceStack> context) {
		return leaveTeam(context, null);
	}

	private static int leaveTeamWithLeader(CommandContext<CommandSourceStack> context) {
		GameProfile leader = resolveProfile(context, "new_leader");
		if (leader == null) return 0;
		return leaveTeam(context, leader.getId());
	}

	private static int leaveTeam(CommandContext<CommandSourceStack> context, @Nullable UUID newLeader) {
		Player player = context.getSource().getPlayer();
		Team team = DatabaseManager.database.getPlayerTeam(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		RoleManager.Check result = TeamLifecycleManager.leaveTeam(player, team, newLeader);
		if (!result.ok()) return fail(context, result);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.leave.success",
			Component.literal(team.getName()).withStyle(ChatFormatting.GOLD))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int transferLeadership(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = DatabaseManager.database.getPlayerTeam(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		GameProfile target = resolveProfile(context, "player");
		if (target == null) return 0;

		RoleManager.Check result = TeamLifecycleManager.transferLeadership(player, team, target.getId());
		if (!result.ok()) return fail(context, result);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.transfer.success",
			Component.literal(target.getName()).withStyle(ChatFormatting.WHITE))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int promptDisband(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = DatabaseManager.database.getPlayerTeam(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		RoleManager.Check allowed = TeamLifecycleManager.canStartDisband(player, team);
		if (!allowed.ok()) return fail(context, allowed);

		if (TeamLifecycleManager.hasApprovers(team, player.getUUID())) {
			TeamLifecycleManager.startDisbandRequest(player, team);
			context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.disband.requested",
				Component.literal(team.getName()).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.YELLOW), true);
			return 1;
		}

		context.getSource().sendSuccess(() ->
			Component.translatable("commands.capitol.team.disband.confirm",
				Component.literal(team.getName()).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED))
				.withStyle(ChatFormatting.RED)
				.append(Component.literal(" "))
				.append(Component.translatable("commands.capitol.team.disband.confirm_yes")
					.withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/capitol team confirm_disband " + team.getName()))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("commands.capitol.team.disband.confirm_hover")))))
				.append(Component.literal(" "))
				.append(Component.translatable("commands.capitol.team.disband.confirm_no")
					.withStyle(s -> s.withColor(ChatFormatting.DARK_RED).withBold(true))),
			false);
		return 1;
	}

	private static int confirmDisband(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = DatabaseManager.database.getPlayerTeam(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		RoleManager.Check allowed = TeamLifecycleManager.canStartDisband(player, team);
		if (!allowed.ok()) return fail(context, allowed);

		// typed-name confirmation to prevent mis-click
		String typed = StringArgumentType.getString(context, "team_name");
		if (!typed.equals(team.getName())) return failCommand(context, "commands.capitol.team.disband.name_mismatch");

		TeamLifecycleManager.finalizeDisband(team);
		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.disband.success").withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int respondDisband(CommandContext<CommandSourceStack> context, boolean approve) {
		Player player = context.getSource().getPlayer();
		Team team = DatabaseManager.database.getPlayerTeam(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		RoleManager.Check result = TeamLifecycleManager.respondDisband(player, team, approve);
		if (!result.ok()) return fail(context, result);

		String key = approve ? "commands.capitol.team.disband.approved" : "commands.capitol.team.disband.cancelled";
		context.getSource().sendSuccess(() -> Component.translatable(key).withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int cancelDisband(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = DatabaseManager.database.getPlayerTeam(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		RoleManager.Check result = TeamLifecycleManager.cancelDisband(player, team);
		if (!result.ok()) return fail(context, result);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.disband.cancelled").withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int disbandStatus(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = DatabaseManager.database.getPlayerTeam(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		DisbandRequest request = DatabaseManager.database.getDisbandRequest(team);
		if (request == null) return failCommand(context, "commands.capitol.team.disband.no_request");

		Map<UUID, Boolean> approvals = DatabaseManager.database.getDisbandApprovals(team);
		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.disband.status_header")
			.withStyle(ChatFormatting.GOLD), false);
		for (Map.Entry<UUID, Boolean> entry : approvals.entrySet()) {
			String name = context.getSource().getServer().getProfileCache().get(entry.getKey())
				.map(GameProfile::getName).orElse("Unknown");
			String state = entry.getValue() ? "\u2714" : "...";
			context.getSource().sendSuccess(() -> Component.literal(name + ": " + state).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMembers(
		CommandContext<CommandSourceStack> context, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		CapitolDatabase database = DatabaseManager.database;
		Team team = database.getPlayerTeam(context.getSource().getPlayer());
		if (team == null) return builder.buildFuture();
		GameProfileCache profileCache = context.getSource().getServer().getProfileCache();
		for (TeamMember member : database.getTeamMembers(team)) {
			profileCache.get(member.playerUUID()).ifPresent(profile -> builder.suggest(profile.getName()));
		}
		return builder.buildFuture();
	}

	@Nullable
	private static GameProfile resolveProfile(CommandContext<CommandSourceStack> context, String argName) {
		String name = StringArgumentType.getString(context, argName);
		Optional<GameProfile> profile = context.getSource().getServer().getProfileCache().get(name);
		if (profile.isEmpty()) {
			TeamRoleCommand.failCommand(context, "commands.capitol.no_player_found", name);
			return null;
		}
		return profile.get();
	}

	private static int fail(CommandContext<CommandSourceStack> context, RoleManager.Check result) {
		return failCommand(context, result.langKey(), result.args());
	}

	private static int failCommand(CommandContext<CommandSourceStack> context, String key, Object... args) {
		return TeamRoleCommand.failCommand(context, key, args);
	}
}