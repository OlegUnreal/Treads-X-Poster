package com.behindthesmile.posting.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chrome_profile_proxy_settings")
public class ChromeProfileProxySettingEntity {
    @Id
    @Column(name = "profile_name", length = 128, nullable = false)
    private String profileName;

    @Column(name = "profile_label", length = 255)
    private String profileLabel;

    @Column(name = "proxy", length = 1024)
    private String proxy;

    @Column(name = "upstream_proxy", length = 1024)
    private String upstreamProxy;

    @Column(name = "proxy_country", length = 64)
    private String proxyCountry;

    @Column(name = "proxy_city", length = 255)
    private String proxyCity;

    @Column(name = "timezone", length = 128)
    private String timezone;

    @Column(name = "language", length = 64)
    private String language;

    @Column(name = "window_size", length = 64)
    private String windowSize;

    @Column(name = "login_status", length = 64)
    private String loginStatus;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public ChromeProfileProxySettingEntity() {
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getProfileLabel() {
        return profileLabel;
    }

    public void setProfileLabel(String profileLabel) {
        this.profileLabel = profileLabel;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public String getUpstreamProxy() {
        return upstreamProxy;
    }

    public void setUpstreamProxy(String upstreamProxy) {
        this.upstreamProxy = upstreamProxy;
    }

    public String getProxyCountry() {
        return proxyCountry;
    }

    public void setProxyCountry(String proxyCountry) {
        this.proxyCountry = proxyCountry;
    }

    public String getProxyCity() {
        return proxyCity;
    }

    public void setProxyCity(String proxyCity) {
        this.proxyCity = proxyCity;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(String windowSize) {
        this.windowSize = windowSize;
    }

    public String getLoginStatus() {
        return loginStatus;
    }

    public void setLoginStatus(String loginStatus) {
        this.loginStatus = loginStatus;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
