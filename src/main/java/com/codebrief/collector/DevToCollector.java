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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects articles from Dev.to
 */
@Slf4j
@Component
public class DevToCollector {

    private final OkHttpClient httpClient;
    private final RetryTemplate retryTemplate;

    @Value("${devto.api.url}")
    private String devtoApiUrl;

    @Value("${devto.tags}")
    private String tags;

    @Value("${devto.min.reactions:10}")
    private int minReactions;

    public DevToCollector(OkHttpClient httpClient, RetryTemplate retryTemplate) {
        this.httpClient = httpClient;
        this.retryTemplate = retryTemplate;
    }

    /**
     * Collect articles from Dev.to with specified tags
     */
    public List<NewsItem> collectArticles() {
        List<NewsItem> items = new ArrayList<>();
        String[] tagList = tags.split(",");

        for (String tag : tagList) {
            try {
                items.addAll(fetchArticlesByTag(tag.trim()));
            } catch (Exception e) {
                log.error("Failed to fetch Dev.to articles for tag {}: {}", tag, e.getMessage());
            }
        }

        log.info("Collected {} Dev.to articles", items.size());
        return items;
    }

    private List<NewsItem> fetchArticlesByTag(String tag) throws Exception {
        List<NewsItem> items = new ArrayList<>();

        String url = String.format("%s/articles?tag=%s&per_page=10&top=1",
                devtoApiUrl, tag);

        String response = retryTemplate.execute(context -> {
            Request request = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.forem.api-v1+json")
                    .build();

            try (Response resp = httpClient.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    throw new RuntimeException("Dev.to API returned " + resp.code());
                }
                return resp.body() != null ? resp.body().string() : "[]";
            }
        });

        JsonArray articles = JsonParser.parseString(response).getAsJsonArray();

        for (JsonElement elem : articles) {
            JsonObject article = elem.getAsJsonObject();
            int reactions = article.has("public_reactions_count")
                    ? article.get("public_reactions_count").getAsInt()
                    : 0;

            if (reactions >= minReactions) {
                LocalDateTime publishedAt = LocalDateTime.parse(
                        article.get("published_at").getAsString(),
                        DateTimeFormatter.ISO_DATE_TIME
                );

                // Get tags
                JsonArray tagsArray = article.getAsJsonArray("tag_list");
                String[] articleTags = new String[tagsArray.size()];
                for (int i = 0; i < tagsArray.size(); i++) {
                    articleTags[i] = tagsArray.get(i).getAsString();
                }

                items.add(NewsItem.builder()
                        .title(article.get("title").getAsString())
                        .url(article.get("url").getAsString())
                        .source("DevTo")
                        .category("Article")
                        .score(reactions)
                        .publishedAt(publishedAt)
                        .description(article.has("description") && !article.get("description").isJsonNull()
                                ? article.get("description").getAsString()
                                : "")
                        .tags(articleTags)
                        .build());
            }
        }

        return items;
    }
}
