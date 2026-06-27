package com.behindthesmile.posting.api;

import java.util.List;

public record PublisherAccountSummary(
        String activeAccountId,
        String activeAccountLabel,
        String xAccountLabel,
        String xModeLabel,
        String threadsAccountLabel,
        List<PublisherAccountOption> availableAccounts
) {
}
