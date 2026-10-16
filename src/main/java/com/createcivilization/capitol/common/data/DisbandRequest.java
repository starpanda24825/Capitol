package com.createcivilization.capitol.common.data;

import java.util.UUID;

// A pending disband request. Approvers and their votes live in disband_approvals.

public record DisbandRequest(UUID teamId, UUID requestedBy, long requestedAt) {}
