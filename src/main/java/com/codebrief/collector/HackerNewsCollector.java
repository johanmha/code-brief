package com.codebrief.collector;

import com.codebrief.model.NewsItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects top stories from Hacker News
 */
@Slf4j
@Component
public class HackerNewsCollector {

    private final OkHttpClient httpClient;
    private final RetryTemplate retryTemplate;

    @Value("${hackernews.api.url}")
    private String hackerNewsApiUrl;

    @Value("${hackernews.min.score:100}")
    private int minScore;

    @Value("${hackernews.max.stories:30}")
    private int maxStories;

    public HackerNewsCollector(OkHttpClient httpClient, RetryTemplate retryTemplate) {
        this.httpClient = httpClient;
        this.retryTemplate = retryTemplate;
    }

    /**
     * Collect top stories from Hacker News
     */
    public List<NewsItem> collectStories() {
        List<NewsItem> items = new ArrayList<>();

        try {
            // Fetch top story IDs
            String topStoriesUrl = hackerNewsApiUrl + "/topstories.json";

            String response = retryTemplate.execute(context -> {
                Request request = new Request.Builder()
                        .url(topStoriesUrl)
                        .build();

                try (Response resp = httpClient.newCall(request).execute()) {
                    if (!resp.isSuccessful()) {
                        throw new RuntimeException("HN API returned " + resp.code());
                    }
                    return resp.body() != null ? resp.body().string() : "[]";
                }
            });

            JsonArray storyIds = JsonParser.parseString(response).getAsJsonArray();

            // Fetch details for top stories
            int checked = 0;
            for (JsonElement idElem : storyIds) {
                if (checked >= maxStories || items.size() >= 10) {
                    break;
                }

                try {
                    NewsItem item = fetchStoryDetails(idElem.getAsInt());
                    if (item != null && item.getScore() != null && item.getScore() >= minScore) {
                        items.add(item);
                    }
                    checked++;
                } catch (Exception e) {
                    log.warn("Failed to fetch HN story {}: {}", idElem.getAsInt(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Failed to fetch HN stories: {}", e.getMessage());
        }

        log.info("Collected {} Hacker News stories", items.size());
        return items;
    }

    private NewsItem fetchStoryDetails(int storyId) throws Exception {
        String url = String.format("%s/item/%d.json", hackerNewsApiUrl, storyId);

        String response = retryTemplate.execute(context -> {
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response resp = httpClient.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    throw new RuntimeException("HN API returned " + resp.code());
                }
                return resp.body() != null ? resp.body().string() : "{}";
            }
        });

        JsonObject story = JsonParser.parseString(response).getAsJsonObject();

        // Only return stories (not jobs, polls, etc.) with URLs
        if (!story.has("type") || !"story".equals(story.get("type").getAsString())) {
            return null;
        }

        if (!story.has("url")) {
            return null;
        }

        String title = story.get("title").getAsString();
        String storyUrl = story.get("url").getAsString();
        int score = story.has("score") ? story.get("score").getAsInt() : 0;
        long time = story.get("time").getAsLong();

        LocalDateTime publishedAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(time),
                ZoneId.systemDefault()
        );

        // Simple frontend keyword filter
        String lowerTitle = title.toLowerCase();
        boolean isFrontendRelated = lowerTitle.contains("javascript") ||
                lowerTitle.contains("react") ||
                lowerTitle.contains("vue") ||
                lowerTitle.contains("angular") ||
                lowerTitle.contains("frontend") ||
                lowerTitle.contains("css") ||
                lowerTitle.contains("web") ||
                lowerTitle.contains("browser") ||
                lowerTitle.contains("typescript") ||
                lowerTitle.contains("node");

        // Only return if frontend-related or very high score
        if (!isFrontendRelated && score < 200) {
            return null;
        }

        return NewsItem.builder()
                .title(title)
                .url(storyUrl)
                .source("HackerNews")
                .category("Top Story")
                .score(score)
                .publishedAt(publishedAt)
                .build();
    }
}
