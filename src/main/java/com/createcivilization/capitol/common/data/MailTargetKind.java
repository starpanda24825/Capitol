package com.createcivilization.capitol.common.data;

// What a mail is addressed to; the sender needs a different permission for each kind.
public enum MailTargetKind {

	MEMBERS,
	ROLES,
	WHOLE_TEAM,
	EXTERNAL;

	public static MailTargetKind fromName(String name) {
		for (MailTargetKind kind : values()) {
			if (kind.name().equalsIgnoreCase(name)) return kind;
		}
		return MEMBERS;
	}
}
