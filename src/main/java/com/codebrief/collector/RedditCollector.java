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
 * Collects top posts from frontend-related subreddits
 */
@Slf4j
@Component
public class RedditCollector {

    private final OkHttpClient httpClient;
    private final RetryTemplate retryTemplate;

    @Value("${reddit.api.url}")
    private String redditApiUrl;

    @Value("${reddit.subreddits}")
    private String subreddits;

    @Value("${reddit.min.upvotes:50}")
    private int minUpvotes;

    @Value("${reddit.time.filter:day}")
    private String timeFilter;

    public RedditCollector(OkHttpClient httpClient, RetryTemplate retryTemplate) {
        this.httpClient = httpClient;
        this.retryTemplate = retryTemplate;
    }

    /**
     * Collect top posts from configured subreddits
     */
    public List<NewsItem> collectPosts() {
        List<NewsItem> items = new ArrayList<>();
        String[] subs = subreddits.split(",");

        for (String sub : subs) {
            try {
                items.addAll(fetchPostsFromSubreddit(sub.trim()));
            } catch (Exception e) {
                log.error("Failed to fetch posts from r/{}: {}", sub, e.getMessage());
            }
        }

        log.info("Collected {} Reddit posts", items.size());
        return items;
    }

    private List<NewsItem> fetchPostsFromSubreddit(String subreddit) throws Exception {
        List<NewsItem> items = new ArrayList<>();

        String url = String.format("%s/r/%s/top.json?t=%s&limit=10",
                redditApiUrl, subreddit, timeFilter);

        String response = retryTemplate.execute(context -> {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "CodeBrief/1.0")
                    .build();

            try (Response resp = httpClient.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    throw new RuntimeException("Reddit API returned " + resp.code());
                }
                return resp.body() != null ? resp.body().string() : "{}";
            }
        });

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonObject data = json.getAsJsonObject("data");
        JsonArray children = data.getAsJsonArray("children");

        for (JsonElement elem : children) {
            JsonObject post = elem.getAsJsonObject().getAsJsonObject("data");
            int score = post.get("ups").getAsInt();

            if (score >= minUpvotes) {
                long createdUtc = post.get("created_utc").getAsLong();
                LocalDateTime publishedAt = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(createdUtc),
                        ZoneId.systemDefault()
                );

                String postUrl = "https://reddit.com" + post.get("permalink").getAsString();

                items.add(NewsItem.builder()
                        .title(post.get("title").getAsString())
                        .url(postUrl)
                        .source("Reddit")
                        .category("r/" + subreddit)
                        .score(score)
                        .publishedAt(publishedAt)
                        .description(post.has("selftext") && !post.get("selftext").isJsonNull()
                                ? truncate(post.get("selftext").getAsString(), 200)
                                : "")
                        .build());
            }
        }

        return items;
    }

    private String truncate(String text, int length) {
        if (text == null || text.length() <= length) {
            return text;
        }
        return text.substring(0, length) + "...";
    }
}
