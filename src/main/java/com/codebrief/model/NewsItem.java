package com.codebrief.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a single news item collected from various sources
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsItem {
    private String title;
    private String url;
    private String source;
    private String category;
    private Integer score;
    private LocalDateTime publishedAt;
    private String description;
    private String[] tags;

    public String getSourceEmoji() {
        return switch (source.toLowerCase()) {
            case "github" -> "🐙";
            case "reddit" -> "🤖";
            case "hackernews" -> "📰";
            case "devto" -> "✍️";
            case "rss" -> "📡";
            case "npm" -> "📦";
            default -> "📌";
        };
    }
}
