package com.behindthesmile.posting.api;

import java.util.List;

public record QueuePostUpsertRequest(
        String topic,
        String text,
        String visualHint,
        String imageUrl,
        String imageSourcePage,
        String imageAttribution,
        String imageLicense,
        String status,
        List<String> platforms,
        List<String> accountIds,
        List<String> targetProfiles,
        String language
) {
}
