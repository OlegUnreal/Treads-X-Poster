package com.behindthesmile.posting.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "queue_posts")
public class QueuedPostEntity {
    @Id
    @Column(name = "post_id", length = 64, nullable = false)
    private String id;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "account_id", length = 128, nullable = false)
    private String accountId;

    @Column(name = "account_label", length = 255)
    private String accountLabel;

    @Column(name = "topic", length = 512)
    private String topic;

    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "platforms", length = 255)
    private String platforms;

    @Column(name = "text", length = 4000, nullable = false)
    private String text;

    @Column(name = "visual_hint", length = 1024)
    private String visualHint;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "image_source_page", length = 2048)
    private String imageSourcePage;

    @Column(name = "image_attribution", length = 2048)
    private String imageAttribution;

    @Column(name = "image_license", length = 2048)
    private String imageLicense;

    @Column(name = "image_original_url", length = 2048)
    private String imageOriginalUrl;

    @Column(name = "published", columnDefinition = "CLOB")
    private String published;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "platform_scope", length = 32)
    private String platformScope;

    public QueuedPostEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAccountLabel() {
        return accountLabel;
    }

    public void setAccountLabel(String accountLabel) {
        this.accountLabel = accountLabel;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getPlatforms() {
        return platforms;
    }

    public void setPlatforms(String platforms) {
        this.platforms = platforms;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getVisualHint() {
        return visualHint;
    }

    public void setVisualHint(String visualHint) {
        this.visualHint = visualHint;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageSourcePage() {
        return imageSourcePage;
    }

    public void setImageSourcePage(String imageSourcePage) {
        this.imageSourcePage = imageSourcePage;
    }

    public String getImageAttribution() {
        return imageAttribution;
    }

    public void setImageAttribution(String imageAttribution) {
        this.imageAttribution = imageAttribution;
    }

    public String getImageLicense() {
        return imageLicense;
    }

    public void setImageLicense(String imageLicense) {
        this.imageLicense = imageLicense;
    }

    public String getImageOriginalUrl() {
        return imageOriginalUrl;
    }

    public void setImageOriginalUrl(String imageOriginalUrl) {
        this.imageOriginalUrl = imageOriginalUrl;
    }

    public String getPublished() {
        return published;
    }

    public void setPublished(String published) {
        this.published = published;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getPlatformScope() {
        return platformScope;
    }

    public void setPlatformScope(String platformScope) {
        this.platformScope = platformScope;
    }
}
