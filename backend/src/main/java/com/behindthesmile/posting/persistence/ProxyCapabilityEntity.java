package com.behindthesmile.posting.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "proxy_capabilities")
public class ProxyCapabilityEntity {
    @Id
    @Column(name = "proxy_host", length = 255, nullable = false)
    private String proxyHost;

    @Column(name = "youtube")
    private Boolean youtube;

    @Column(name = "pornhub")
    private Boolean pornhub;

    protected ProxyCapabilityEntity() {
    }

    public ProxyCapabilityEntity(String proxyHost, Boolean youtube, Boolean pornhub) {
        this.proxyHost = proxyHost;
        this.youtube = youtube;
        this.pornhub = pornhub;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
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
}
