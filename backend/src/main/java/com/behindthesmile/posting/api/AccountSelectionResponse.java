package com.behindthesmile.posting.api;

import java.util.List;

public record AccountSelectionResponse(
        String activeAccountId,
        List<PublisherAccountOption> accounts
) {
}
