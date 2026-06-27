package com.behindthesmile.posting.service;

import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.time.Duration;

@Service
public class HttpService {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public HttpClient client() {
        return client;
    }
}
