package com.behindthesmile.posting.api;

public record ActionResult(
        boolean success,
        String command,
        String message
) {
}
