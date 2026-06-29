package com.behindthesmile.posting.model;

import java.util.List;
import java.util.Map;

public class QueuedPost {
    private String id;
    private String status;
    private String createdAt;
    private String accountId;
    private String accountLabel;
    private String topic;
    private String tone;
    private String language;
    private List<String> platforms;
    private String text;
    private String visualHint;
    private String imageUrl;
    private String imageSourcePage;
    private String imageAttribution;
    private String imageLicense;
    private String imageOriginalUrl;
    private Map<String, PublishedInfo> published;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getAccountLabel() { return accountLabel; }
    public void setAccountLabel(String accountLabel) { this.accountLabel = accountLabel; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public List<String> getPlatforms() { return platforms; }
    public void setPlatforms(List<String> platforms) { this.platforms = platforms; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getVisualHint() { return visualHint; }
    public void setVisualHint(String visualHint) { this.visualHint = visualHint; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getImageSourcePage() { return imageSourcePage; }
    public void setImageSourcePage(String imageSourcePage) { this.imageSourcePage = imageSourcePage; }
    public String getImageAttribution() { return imageAttribution; }
    public void setImageAttribution(String imageAttribution) { this.imageAttribution = imageAttribution; }
    public String getImageLicense() { return imageLicense; }
    public void setImageLicense(String imageLicense) { this.imageLicense = imageLicense; }
    public String getImageOriginalUrl() { return imageOriginalUrl; }
    public void setImageOriginalUrl(String imageOriginalUrl) { this.imageOriginalUrl = imageOriginalUrl; }
    public Map<String, PublishedInfo> getPublished() { return published; }
    public void setPublished(Map<String, PublishedInfo> published) { this.published = published; }

    public static class PublishedInfo {
        private String at;
        private Object result;

        public String getAt() { return at; }
        public void setAt(String at) { this.at = at; }
        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
    }
}
