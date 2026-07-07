package com.chat.learning.model;

public class LearningCategory {
    public String id;
    public String title;
    public String subtitle;
    public int count;
    public String cover;
    public String action;

    public LearningCategory(String id, String title, String subtitle, int count, String cover, String action) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.count = count;
        this.cover = cover;
        this.action = action;
    }
}
