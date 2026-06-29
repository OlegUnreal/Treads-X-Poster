package com.behindthesmile.posting.api;

public record YoutubePlaybackRequest(
        String url,
        Integer percent
) {
}
