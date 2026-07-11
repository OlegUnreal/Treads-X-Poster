package com.behindthesmile.posting.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "proxies")
public class ProxyInventoryEntity {
    @Id
    @Column(name = "proxy_key", length = 255, nullable = false)
    private String proxyKey;

    @Column(name = "proxy", length = 1024, nullable = false)
    private String proxy;

    @Column(name = "host", length = 255, nullable = false)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "country", length = 64)
    private String country;

    @Column(name = "city", length = 255)
    private String city;

    @Column(name = "source", length = 128)
    private String source;

    @Column(name = "youtube")
    private Boolean youtube;

    @Column(name = "pornhub")
    private Boolean pornhub;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getProxyKey() {
        return proxyKey;
    }

    public void setProxyKey(String proxyKey) {
        this.proxyKey = proxyKey;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getYoutube() {
        return youtube;
    }

    public void setYoutube(Boolean youtube) {
        this.youtube = youtube;
    }

    public Boolean getPornhub() {
        return pornhub;
    }

    public void setPornhub(Boolean pornhub) {
        this.pornhub = pornhub;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
