package com.codebrief.service;

import com.codebrief.model.Digest;
import com.codebrief.model.NewsItem;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Posts digest to Slack via webhook
 */
@Slf4j
@Service
public class SlackService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final RetryTemplate retryTemplate;

    @Value("${slack.webhook.url}")
    private String webhookUrl;

    public SlackService(OkHttpClient httpClient, RetryTemplate retryTemplate) {
        this.httpClient = httpClient;
        this.retryTemplate = retryTemplate;
    }

    /**
     * Post digest to Slack
     */
    public void postDigest(Digest digest) {
        log.info("Posting digest to Slack...");

        try {
            String message = formatDigest(digest);
            sendToSlack(message);
            log.info("Successfully posted digest to Slack");
        } catch (Exception e) {
            log.error("Failed to post to Slack: {}", e.getMessage());
            throw new RuntimeException("Failed to post to Slack", e);
        }
    }

    private String formatDigest(Digest digest) {
        StringBuilder sb = new StringBuilder();

        sb.append("📬 *Code Brief - ").append(digest.getDate()).append("*\n\n");

        // Top Updates
        if (!digest.getTopUpdates().isEmpty()) {
            sb.append("🔥 *Top Updates*\n");
            for (NewsItem item : digest.getTopUpdates()) {
                sb.append(formatNewsItem(item, true));
            }
            sb.append("\n");
        }

        // Quick Mentions
        if (!digest.getQuickMentions().isEmpty()) {
            sb.append("📰 *Quick Mentions*\n");
            for (NewsItem item : digest.getQuickMentions()) {
                sb.append(formatNewsItem(item, false));
            }
            sb.append("\n");
        }

        // Community Buzz
        if (!digest.getCommunityBuzz().isEmpty()) {
            sb.append("💬 *Community Buzz*\n");
            for (NewsItem item : digest.getCommunityBuzz()) {
                sb.append(formatNewsItem(item, true));
            }
            sb.append("\n");
        }

        sb.append("────────────────────────────────\n");
        sb.append("_Curated by Gemini AI • Running on GitHub Actions_");

        return sb.toString();
    }

    private String formatNewsItem(NewsItem item, boolean includeScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("• ");

        // Add emoji for source
        sb.append(item.getSourceEmoji()).append(" ");

        // Add title with link
        sb.append("<").append(item.getUrl()).append("|").append(item.getTitle()).append(">");

        // Add score if available and requested
        if (includeScore && item.getScore() != null) {
            sb.append(" (").append(item.getScore());

            switch (item.getSource().toLowerCase()) {
                case "reddit":
                    sb.append(" upvotes");
                    break;
                case "hackernews":
                    sb.append(" points");
                    break;
                case "github":
                    sb.append(" stars");
                    break;
                case "devto":
                    sb.append(" reactions");
                    break;
            }

            sb.append(")");
        }

        sb.append("\n");
        return sb.toString();
    }

    private void sendToSlack(String message) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("text", message);
        payload.addProperty("mrkdwn", true);

        RequestBody body = RequestBody.create(payload.toString(), JSON);

        retryTemplate.execute(context -> {
            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    throw new RuntimeException("Slack webhook returned " + response.code() + ": " + errorBody);
                }
                return response.body() != null ? response.body().string() : "ok";
            }
        });
    }

    /**
     * Test the Slack webhook
     */
    public void testWebhook() {
        log.info("Testing Slack webhook...");
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("text", "🎉 Code Brief is set up and ready to go!");

            RequestBody body = RequestBody.create(payload.toString(), JSON);
            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("Slack webhook test successful!");
                } else {
                    log.error("Slack webhook test failed: {}", response.code());
                }
            }
        } catch (Exception e) {
            log.error("Slack webhook test failed: {}", e.getMessage());
        }
    }
}
