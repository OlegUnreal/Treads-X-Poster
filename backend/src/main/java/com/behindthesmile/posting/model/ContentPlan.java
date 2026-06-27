package com.behindthesmile.posting.model;

import java.util.List;

public class ContentPlan {
    private List<Item> items;

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public static class Item {
        private String topic;
        private String tone;
        private String language;
        private Integer count;
        private List<String> platforms;

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getTone() { return tone; }
        public void setTone(String tone) { this.tone = tone; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public List<String> getPlatforms() { return platforms; }
        public void setPlatforms(List<String> platforms) { this.platforms = platforms; }
    }
}
