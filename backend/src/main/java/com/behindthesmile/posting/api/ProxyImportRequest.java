package com.behindthesmile.posting.api;

import java.util.List;

public record ProxyImportRequest(
        String source,
        String country,
        String city,
        List<ProxyImportItem> proxies
) {
}
