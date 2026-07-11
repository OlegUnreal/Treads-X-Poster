package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.ProxyImportItem;
import com.behindthesmile.posting.api.ProxyImportRequest;
import com.behindthesmile.posting.persistence.ProxyInventoryEntity;
import com.behindthesmile.posting.persistence.ProxyInventoryRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProxyInventoryService {
    private final ProxyInventoryRepository proxyInventoryRepository;

    public ProxyInventoryService(ProxyInventoryRepository proxyInventoryRepository) {
        this.proxyInventoryRepository = proxyInventoryRepository;
    }

    public Map<String, Object> listProxies() {
        List<Map<String, Object>> proxies = proxyInventoryRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
        return Map.of(
                "count", proxies.size(),
                "proxies", proxies
        );
    }

    public Map<String, Object> importProxies(ProxyImportRequest request) {
        if (request == null || request.proxies() == null || request.proxies().isEmpty()) {
            throw new IllegalArgumentException("Request must contain proxies.");
        }
        if (request.proxies().size() > 1000) {
            throw new IllegalArgumentException("Too many proxies in one request. Maximum is 1000.");
        }

        int created = 0;
        int updated = 0;
        List<Map<String, Object>> saved = new ArrayList<>();
        Instant now = Instant.now();
        for (ProxyImportItem item : request.proxies()) {
            ParsedProxy parsed = parseProxy(item);
            String proxyKey = parsed.proxyKey();
            ProxyInventoryEntity entity = proxyInventoryRepository.findById(proxyKey).orElse(null);
            boolean isNew = entity == null;
            if (isNew) {
                entity = new ProxyInventoryEntity();
                entity.setProxyKey(proxyKey);
                entity.setCreatedAt(now);
                created++;
            } else {
                updated++;
            }

            entity.setProxy(parsed.proxy());
            entity.setHost(parsed.host());
            entity.setPort(parsed.port());
            entity.setCountry(firstNonBlank(item.country(), request.country()));
            entity.setCity(firstNonBlank(item.city(), request.city()));
            entity.setSource(defaultString(firstNonBlank(item.source(), request.source()), "manual"));
            if (item.youtube() != null || isNew) {
                entity.setYoutube(item.youtube());
            }
            if (item.pornhub() != null || isNew) {
                entity.setPornhub(item.pornhub());
            }
            if (item.active() != null || isNew) {
                entity.setActive(item.active() == null || item.active());
            }
            entity.setUpdatedAt(now);
            saved.add(toResponse(proxyInventoryRepository.save(entity)));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "proxies imported");
        response.put("created", created);
        response.put("updated", updated);
        response.put("count", saved.size());
        response.put("proxies", saved);
        return response;
    }

    private Map<String, Object> toResponse(ProxyInventoryEntity entity) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("proxyKey", entity.getProxyKey());
        response.put("proxy", maskProxy(entity.getProxy()));
        response.put("host", entity.getHost());
        response.put("port", entity.getPort());
        response.put("country", entity.getCountry());
        response.put("city", entity.getCity());
        response.put("source", entity.getSource());
        response.put("youtube", entity.getYoutube());
        response.put("pornhub", entity.getPornhub());
        response.put("active", entity.isActive());
        response.put("createdAt", entity.getCreatedAt());
        response.put("updatedAt", entity.getUpdatedAt());
        return response;
    }

    private ParsedProxy parseProxy(ProxyImportItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Proxy item cannot be null.");
        }
        String raw = firstNonBlank(item.proxy(), item.url());
        String host = clean(item.host());
        Integer port = item.port();
        if (host.isBlank() && raw.isBlank()) {
            throw new IllegalArgumentException("Each proxy must contain proxy/url or host.");
        }

        if (!raw.isBlank()) {
            ParsedProxy parsed = parseProxyValue(raw);
            host = parsed.host();
            port = parsed.port();
        } else {
            raw = port == null ? host : host + ":" + port;
        }

        if (host.isBlank()) {
            throw new IllegalArgumentException("Could not detect proxy host from: " + raw);
        }
        String key = port == null
                ? host.toLowerCase(Locale.ROOT)
                : (host + ":" + port).toLowerCase(Locale.ROOT);
        return new ParsedProxy(key, raw.trim(), host, port);
    }

    private ParsedProxy parseProxyValue(String rawProxy) {
        String value = rawProxy.trim();
        try {
            URI uri = URI.create(value.contains("://") ? value : "http://" + value);
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                Integer port = uri.getPort() > 0 ? uri.getPort() : null;
                String key = port == null ? uri.getHost() : uri.getHost() + ":" + port;
                return new ParsedProxy(key.toLowerCase(Locale.ROOT), value, uri.getHost(), port);
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to host:port:user:password style parsing below.
        }

        String[] parts = value.split(":");
        if (parts.length >= 2) {
            Integer port = parsePort(parts[1]);
            if (port != null) {
                return new ParsedProxy((parts[0] + ":" + port).toLowerCase(Locale.ROOT), value, parts[0], port);
            }
        }
        return new ParsedProxy(value.toLowerCase(Locale.ROOT), value, value, null);
    }

    private Integer parsePort(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 && parsed <= 65535 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        String cleanedFirst = clean(first);
        return cleanedFirst.isBlank() ? clean(second) : cleanedFirst;
    }

    private String defaultString(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String maskProxy(String proxy) {
        if (proxy == null || proxy.isBlank()) {
            return "";
        }
        return proxy.replaceAll("(https?://)[^:@/]+:[^@/]+@", "$1***:***@");
    }

    private record ParsedProxy(String proxyKey, String proxy, String host, Integer port) {
    }
}
