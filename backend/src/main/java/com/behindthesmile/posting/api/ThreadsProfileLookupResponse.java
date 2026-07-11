package com.behindthesmile.posting.api;

public record ThreadsProfileLookupResponse(
        String username,
        String name,
        String label,
        String profilePictureUrl
) {
}
