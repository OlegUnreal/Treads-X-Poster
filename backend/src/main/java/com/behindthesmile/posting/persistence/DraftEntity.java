package com.behindthesmile.posting.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "generated_drafts")
public class DraftEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, length = 64)
    private String createdAt;

    @Column(name = "topic", length = 512)
    private String topic;

    @Column(name = "text", length = 4000, nullable = false)
    private String text;

    @Column(name = "visual_hint", length = 1024)
    private String visualHint;

    protected DraftEntity() {
    }

    public DraftEntity(String createdAt, String topic, String text, String visualHint) {
        this.createdAt = createdAt;
        this.topic = topic;
        this.text = text;
        this.visualHint = visualHint;
    }

    public Long getId() {
        return id;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
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
}

