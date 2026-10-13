package com.createcivilization.capitol.server.commands.team;

import com.createcivilization.capitol.common.data.Permission;
import com.createcivilization.capitol.common.data.Team;
import com.createcivilization.capitol.common.data.TeamMember;
import com.createcivilization.capitol.common.data.TeamRole;
import com.createcivilization.capitol.common.managers.DatabaseManager;
import com.createcivilization.capitol.common.managers.RoleManager;
import com.createcivilization.capitol.common.modules.database.CapitolDatabase;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;

class TeamRoleCommand {

	static LiteralArgumentBuilder<CommandSourceStack> register() {
		return Commands.literal("role")
			.then(Commands.literal("create")
				.then(Commands.argument("role_name", StringArgumentType.string())
					.then(Commands.argument("color", StringArgumentType.word())
						.executes(TeamRoleCommand::createRoleWithColor))
					.executes(TeamRoleCommand::createRole)))
			.then(Commands.literal("edit")
				.then(roleArg()
					.then(Commands.literal("name")
						.then(Commands.argument("new_name", StringArgumentType.string())
							.executes(TeamRoleCommand::editRoleName)))
					.then(Commands.literal("color")
						.then(Commands.argument("color", StringArgumentType.word())
							.executes(TeamRoleCommand::editRoleColor)))
					.then(Commands.literal("permission")
						.then(Commands.argument("permission_value", StringArgumentType.word())
							.suggests((context, builder) -> {
								for (Permission permission : Permission.values()) {
									builder.suggest(permission.name().toLowerCase());
								}
								return builder.buildFuture();
							})
							.executes(TeamRoleCommand::editRolePerms)))
					.then(Commands.literal("move")
						.then(Commands.literal("up").executes(context -> moveRole(context, 1)))
						.then(Commands.literal("down").executes(context -> moveRole(context, -1))))
					.then(Commands.literal("list").executes(TeamRoleCommand::viewRolePermList))
					.then(Commands.literal("delete").executes(TeamRoleCommand::deleteRole))))
			.then(Commands.literal("assign")
				.then(roleArg()
					.then(Commands.argument("player", StringArgumentType.string())
						.suggests(TeamRoleCommand::suggestMembers)
						.executes(TeamRoleCommand::assignRole))))
			.then(Commands.literal("unassign")
				.then(roleArg()
					.then(Commands.argument("player", StringArgumentType.string())
						.suggests(TeamRoleCommand::suggestMembers)
						.executes(TeamRoleCommand::unassignRole))))
			.then(Commands.literal("members")
				.then(roleArg().executes(TeamRoleCommand::viewRoleMembers)));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> roleArg() {
		return Commands.argument("role_name", StringArgumentType.string())
			.suggests((context, builder) -> {
				Player player = context.getSource().getPlayer();
				if (player == null) return builder.buildFuture();
				Team team = DatabaseManager.database.getPlayerTeam(player);
				if (team == null) return builder.buildFuture();
				for (TeamRole role : DatabaseManager.database.getTeamRoles(team)) builder.suggest(role.name());
				return builder.buildFuture();
			});
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMembers(
		CommandContext<CommandSourceStack> context,
		com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		CapitolDatabase database = DatabaseManager.database;
		Team team = database.getPlayerTeam(context.getSource().getPlayer());
		if (team == null) return builder.buildFuture();
		GameProfileCache profileCache = context.getSource().getServer().getProfileCache();
		for (TeamMember member : database.getTeamMembers(team)) {
			profileCache.get(member.playerUUID()).ifPresent(profile -> builder.suggest(profile.getName()));
		}
		return builder.buildFuture();
	}

	private static int createRole(CommandContext<CommandSourceStack> context) {
		return createRole(context, 0xFFFFFF);
	}

	private static int createRoleWithColor(CommandContext<CommandSourceStack> context) {
		Integer color = parseColor(StringArgumentType.getString(context, "color"));
		if (color == null) {
			return failCommand(context, "commands.capitol.team.create.invalid_color", StringArgumentType.getString(context, "color"));
		}
		return createRole(context, color);
	}

	private static int createRole(CommandContext<CommandSourceStack> context, int color) {
		ServerPlayer player = context.getSource().getPlayer();
		Team team = teamOf(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		String name = StringArgumentType.getString(context, "role_name");
		RoleManager.Check result = RoleManager.createRole(player, team, name, color, 0);
		if (!result.ok()) return fail(context, result);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.role.create.success",
			Component.literal(name.trim()).withStyle(ChatFormatting.AQUA),
			Component.literal(team.getName()).withStyle(ChatFormatting.GOLD))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int deleteRole(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		Team team = teamOf(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		TeamRole role = resolveRole(context, team, player);
		if (role == null) return 0;

		int affected = DatabaseManager.database.getRoleMembers(team, role.id()).size();
		RoleManager.Check result = RoleManager.deleteRole(player, team, role);
		if (!result.ok()) return fail(context, result);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.role.remove.success",
			Component.literal(role.name()).withStyle(ChatFormatting.AQUA))
			.withStyle(ChatFormatting.GRAY), true);
		if (affected > 0) {
			context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.role.remove.affected", affected)
				.withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int editRoleName(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		Team team = teamOf(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		TeamRole role = resolveRole(context, team, player);
		if (role == null) return 0;

		String newName = StringArgumentType.getString(context, "new_name");
		RoleManager.Check result = RoleManager.updateRole(player, team, role, newName, null, null, null);
		if (!result.ok()) return fail(context, result);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.role.name.success",
			Component.literal(role.name()).withStyle(ChatFormatting.AQUA),
			Component.literal(newName.trim()).withStyle(ChatFormatting.AQUA))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int editRoleColor(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		Team team = teamOf(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		TeamRole role = resolveRole(context, team, player);
		if (role == null) return 0;

		Integer color = parseColor(StringArgumentType.getString(context, "color"));
		if (color == null) {
			return failCommand(context, "commands.capitol.team.create.invalid_color", StringArgumentType.getString(context, "color"));
		}
		RoleManager.Check result = RoleManager.updateRole(player, team, role, null, color, null, null);
		if (!result.ok()) return fail(context, result);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.role.color.success",
			Component.literal(role.name()).withStyle(ChatFormatting.AQUA))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int editRolePerms(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		Team team = teamOf(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		TeamRole role = resolveRole(context, team, player);
		if (role == null) return 0;

		String permissionName = StringArgumentType.getString(context, "permission_value").toUpperCase();
		Permission permission;
		try {
			permission = Permission.valueOf(permissionName);
		} catch (IllegalArgumentException e) {
			return failCommand(context, "commands.capitol.invalid_permission", permissionName);
		}

		long newPerms = permission.toggle(role.permissions());
		boolean newState = permission.hasPermission(newPerms);
		RoleManager.Check result = RoleManager.setRolePermissions(player, team, role, newPerms);
		if (!result.ok()) return fail(context, result);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.permission_toggle",
			Component.literal(permissionName.toLowerCase()).withStyle(ChatFormatting.AQUA),
			Component.literal(role.name()).withStyle(ChatFormatting.GOLD),
			Component.literal(String.valueOf(!newState)).withStyle(!newState ? ChatFormatting.GREEN : ChatFormatting.RED),
			Component.literal(String.valueOf(newState)).withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.RED))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int moveRole(CommandContext<CommandSourceStack> context, int delta) {
		ServerPlayer player = context.getSource().getPlayer();
		Team team = teamOf(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		TeamRole role = resolveRole(context, team, player);
		if (role == null) return 0;

		RoleManager.Check result = RoleManager.moveRole(player, team, role, delta);
		if (!result.ok()) return fail(context, result);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.role.move.success",
			Component.literal(role.name()).withStyle(ChatFormatting.AQUA))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int assignRole(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		Team team = teamOf(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		TeamRole role = resolveRole(context, team, player);
		if (role == null) return 0;

		GameProfile target = resolveTarget(context);
		if (target == null) return 0;

		RoleManager.Check result = RoleManager.assignRole(player, team, role, target.getId());
		if (!result.ok()) return fail(context, result);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.role.assign.success",
			Component.literal(target.getName()).withStyle(ChatFormatting.WHITE),
			Component.literal(role.name()).withStyle(ChatFormatting.AQUA))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int unassignRole(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		Team team = teamOf(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		TeamRole role = resolveRole(context, team, player);
		if (role == null) return 0;

		GameProfile target = resolveTarget(context);
		if (target == null) return 0;

		RoleManager.Check result = RoleManager.unassignRole(player, team, role, target.getId());
		if (!result.ok()) return fail(context, result);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.role.unassign.success",
			Component.literal(target.getName()).withStyle(ChatFormatting.WHITE),
			Component.literal(role.name()).withStyle(ChatFormatting.AQUA))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int viewRoleMembers(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		Team team = teamOf(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		TeamRole role = resolveRole(context, team, player);
		if (role == null) return 0;

		List<java.util.UUID> members = DatabaseManager.database.getRoleMembers(team, role.id());
		if (members.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.role.members.empty")
				.withStyle(ChatFormatting.GRAY), false);
			return 1;
		}
		for (java.util.UUID memberId : members) {
			String name = context.getSource().getServer().getProfileCache().get(memberId)
				.map(GameProfile::getName).orElse("Unknown");
			context.getSource().sendSuccess(() -> Component.literal(name).withStyle(ChatFormatting.WHITE), false);
		}
		return 1;
	}

	private static int viewRolePermList(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		Team team = teamOf(player);
		if (team == null) return failCommand(context, "commands.capitol.not_in_team_error");

		TeamRole role = resolveRole(context, team, player);
		if (role == null) return 0;

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.team.role.permission_list.role")
			.append(Component.literal(role.name())).withStyle(ChatFormatting.GOLD), false);

		for (Permission permission : Permission.values()) {
			MutableComponent component = Component.literal("[")
				.append(permission.hasPermission(role.permissions())
					? Component.literal("\u2714").withStyle(ChatFormatting.GREEN)
					: Component.literal("\u274C").withStyle(ChatFormatting.RED))
				.append("]");
			component.withStyle(style -> style
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
					String.format("/capitol team role edit %s permission %s", role.name(), permission.name().toLowerCase())))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
					Component.translatable("commands.capitol.team.role.permission_list.hover"))));
			context.getSource().sendSuccess(() -> component
				.append(Component.literal(" ").withStyle(ChatFormatting.GOLD))
				.append(Component.literal(permission.name()).withStyle(ChatFormatting.WHITE)), false);
		}
		return 1;
	}

	// --- helpers ---

	private static Team teamOf(Player player) {
		return player == null ? null : DatabaseManager.database.getPlayerTeam(player);
	}

	private static TeamRole resolveRole(CommandContext<CommandSourceStack> context, Team team, Player player) {
		String name = StringArgumentType.getString(context, "role_name");
		TeamRole role = DatabaseManager.database.getRoleByName(team, name);
		if (role == null) {
			failCommand(context, "commands.capitol.team.role.not_found", name, team.getName());
			return null;
		}
		return role;
	}

	private static GameProfile resolveTarget(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "player");
		Optional<GameProfile> profile = context.getSource().getServer().getProfileCache().get(name);
		if (profile.isEmpty()) {
			failCommand(context, "commands.capitol.no_player_found", name);
			return null;
		}
		return profile.get();
	}

	private static Integer parseColor(String input) {
		try {
			return (int) Long.parseLong(input.replace("#", ""), 16);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int fail(CommandContext<CommandSourceStack> context, RoleManager.Check result) {
		return failCommand(context, result.langKey(), result.args());
	}

	static int failCommand(CommandContext<CommandSourceStack> context, String key, Object... args) {
		context.getSource().sendFailure(Component.translatable(key, args).withStyle(ChatFormatting.RED));
		return 0;
	}
}