package com.codebrief.service;

import com.codebrief.model.Digest;
import com.codebrief.model.NewsItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Uses Gemini AI to analyze and summarize news items
 */
@Slf4j
@Service
public class GeminiService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final RetryTemplate retryTemplate;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    @Value("${gemini.max.tokens:2048}")
    private int maxTokens;

    @Value("${gemini.temperature:0.7}")
    private double temperature;

    public GeminiService(OkHttpClient httpClient, RetryTemplate retryTemplate) {
        this.httpClient = httpClient;
        this.retryTemplate = retryTemplate;
    }

    /**
     * Process news items into a digest using Gemini AI
     */
    public Digest processNewsItems(List<NewsItem> items) {
        log.info("Processing {} news items with Gemini AI...", items.size());

        if (items.isEmpty()) {
            log.warn("No news items to process");
            return createEmptyDigest();
        }

        try {
            String prompt = buildPrompt(items);
            String response = callGeminiAPI(prompt);
            return parseDigestResponse(response, items);
        } catch (Exception e) {
            log.error("Failed to process news with Gemini: {}", e.getMessage());
            return createFallbackDigest(items);
        }
    }

    private String buildPrompt(List<NewsItem> items) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a frontend development news curator. Analyze the following news items and create a concise daily digest.\n\n");
        prompt.append("NEWS ITEMS:\n");

        int count = 1;
        for (NewsItem item : items) {
            prompt.append(String.format("%d. [%s] %s (Score: %s)\n",
                    count++,
                    item.getSource(),
                    item.getTitle(),
                    item.getScore() != null ? item.getScore() : "N/A"
            ));

            if (item.getDescription() != null && !item.getDescription().isEmpty()) {
                prompt.append("   Description: ").append(item.getDescription()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("\nTASK:\n");
        prompt.append("1. Identify the TOP 3 most important updates (framework releases, major news)\n");
        prompt.append("2. List 3-5 quick mentions (interesting but less critical)\n");
        prompt.append("3. Highlight 2-3 community discussions (Reddit/HN posts with high engagement)\n\n");

        prompt.append("OUTPUT FORMAT (respond with ONLY this JSON, no markdown formatting):\n");
        prompt.append("{\n");
        prompt.append("  \"topUpdates\": [1, 3, 7],  // indices of top 3 items\n");
        prompt.append("  \"quickMentions\": [2, 5, 9, 12],  // indices of 3-5 items\n");
        prompt.append("  \"communityBuzz\": [4, 8]  // indices of 2-3 discussion items\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    private String callGeminiAPI(String prompt) throws Exception {
        String url = apiUrl + "?key=" + apiKey;

        JsonObject requestBody = new JsonObject();

        // Build contents array
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);

        // Generation config
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", temperature);
        generationConfig.addProperty("maxOutputTokens", maxTokens);
        requestBody.add("generationConfig", generationConfig);

        RequestBody body = RequestBody.create(requestBody.toString(), JSON);

        return retryTemplate.execute(context -> {
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    throw new RuntimeException("Gemini API returned " + response.code() + ": " + errorBody);
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                return jsonResponse
                        .getAsJsonArray("candidates").get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts").get(0).getAsJsonObject()
                        .get("text").getAsString();
            }
        });
    }

    private Digest parseDigestResponse(String response, List<NewsItem> items) {
        try {
            // Extract JSON from response (remove markdown formatting if present)
            String jsonStr = response.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();

            List<NewsItem> topUpdates = extractItems(json, "topUpdates", items);
            List<NewsItem> quickMentions = extractItems(json, "quickMentions", items);
            List<NewsItem> communityBuzz = extractItems(json, "communityBuzz", items);

            return Digest.builder()
                    .date(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")))
                    .topUpdates(topUpdates)
                    .quickMentions(quickMentions)
                    .communityBuzz(communityBuzz)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return createFallbackDigest(items);
        }
    }

    private List<NewsItem> extractItems(JsonObject json, String key, List<NewsItem> allItems) {
        List<NewsItem> result = new ArrayList<>();

        if (!json.has(key)) {
            return result;
        }

        JsonArray indices = json.getAsJsonArray(key);
        for (int i = 0; i < indices.size(); i++) {
            int index = indices.get(i).getAsInt() - 1; // Convert to 0-based
            if (index >= 0 && index < allItems.size()) {
                result.add(allItems.get(index));
            }
        }

        return result;
    }

    private Digest createEmptyDigest() {
        return Digest.builder()
                .date(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")))
                .topUpdates(new ArrayList<>())
                .quickMentions(new ArrayList<>())
                .communityBuzz(new ArrayList<>())
                .build();
    }

    private Digest createFallbackDigest(List<NewsItem> items) {
        // Simple fallback: sort by score and divide into sections
        List<NewsItem> sorted = items.stream()
                .sorted((a, b) -> {
                    Integer scoreA = a.getScore() != null ? a.getScore() : 0;
                    Integer scoreB = b.getScore() != null ? b.getScore() : 0;
                    return scoreB.compareTo(scoreA);
                })
                .collect(Collectors.toList());

        int size = sorted.size();
        List<NewsItem> topUpdates = sorted.subList(0, Math.min(3, size));
        List<NewsItem> quickMentions = sorted.subList(Math.min(3, size), Math.min(8, size));
        List<NewsItem> communityBuzz = sorted.subList(Math.min(8, size), Math.min(10, size));

        return Digest.builder()
                .date(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")))
                .topUpdates(topUpdates)
                .quickMentions(quickMentions)
                .communityBuzz(communityBuzz)
                .build();
    }
}
