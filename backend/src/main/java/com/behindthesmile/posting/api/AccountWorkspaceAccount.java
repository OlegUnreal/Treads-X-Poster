package com.behindthesmile.posting.api;

public record AccountWorkspaceAccount(
        String id,
        String label,
        String prompt,
        String language,
        Integer defaultPostCount,
        String xAccountLabel,
        String xModeLabel,
        boolean xConfigured,
        long xReady,
        long xFailed,
        String threadsAccountLabel,
        boolean threadsConfigured,
        long threadsReady,
        long threadsFailed,
        long mediaAttached,
        long textOnly,
        long published
) {
}
