package com.behindthesmile.posting.api;

public record ProxyImportItem(
        String proxy,
        String url,
        String host,
        Integer port,
        String country,
        String city,
        String source,
        Boolean youtube,
        Boolean pornhub,
        Boolean active
) {
}
