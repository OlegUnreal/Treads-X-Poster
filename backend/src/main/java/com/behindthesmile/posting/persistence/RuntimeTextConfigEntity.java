package com.behindthesmile.posting.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "runtime_text_configs")
public class RuntimeTextConfigEntity {
    @Id
    @Column(name = "config_key", length = 128, nullable = false)
    private String key;

    @Column(name = "content_type", length = 128, nullable = false)
    private String contentType;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "updated_at", length = 64, nullable = false)
    private String updatedAt;

    public RuntimeTextConfigEntity() {
    }

    public RuntimeTextConfigEntity(String key, String contentType, String content, String updatedAt) {
        this.key = key;
        this.contentType = contentType;
        this.content = content;
        this.updatedAt = updatedAt;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
