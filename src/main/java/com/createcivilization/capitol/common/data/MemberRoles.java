package com.createcivilization.capitol.common.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


// A member's base role (owner or member) plus any extra custom roles stacked on top of it.

public record MemberRoles(UUID player, TeamRole base, List<TeamRole> extra) {

	public List<TeamRole> all() {
		List<TeamRole> roles = new ArrayList<>();
		if (base != null) roles.add(base);
		if (extra != null) roles.addAll(extra);
		return roles;
	}

	public boolean isOwner() {
		return base != null && base.isOwner();
	}
}
