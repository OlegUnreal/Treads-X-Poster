package com.behindthesmile.posting.api;

import java.util.List;

public record AccountWorkspaceSummary(
        String activeAccountId,
        long totalReady,
        long totalFailed,
        long totalPublished,
        List<AccountWorkspaceAccount> accounts
) {
}
