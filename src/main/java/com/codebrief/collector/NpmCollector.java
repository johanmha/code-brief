package com.codebrief.collector;

import com.codebrief.model.NewsItem;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Collects package information and trends from npm
 */
@Slf4j
@Component
public class NpmCollector {

    private final OkHttpClient httpClient;
    private final RetryTemplate retryTemplate;

    @Value("${npm.registry.url}")
    private String npmRegistryUrl;

    @Value("${npm.packages}")
    private String packages;

    @Value("${news.collection.hours:24}")
    private int collectionHours;

    public NpmCollector(OkHttpClient httpClient, RetryTemplate retryTemplate) {
        this.httpClient = httpClient;
        this.retryTemplate = retryTemplate;
    }

    /**
     * Collect package information from npm
     */
    public List<NewsItem> collectPackageInfo() {
        List<NewsItem> items = new ArrayList<>();
        String[] packageList = packages.split(",");

        for (String pkg : packageList) {
            try {
                NewsItem item = fetchPackageInfo(pkg.trim());
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception e) {
                log.error("Failed to fetch npm package {}: {}", pkg, e.getMessage());
            }
        }

        log.info("Collected {} npm package updates", items.size());
        return items;
    }

    private NewsItem fetchPackageInfo(String packageName) throws Exception {
        String url = String.format("%s/%s", npmRegistryUrl, packageName);

        String response = retryTemplate.execute(context -> {
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response resp = httpClient.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    if (resp.code() == 404) {
                        return null;
                    }
                    throw new RuntimeException("npm API returned " + resp.code());
                }
                return resp.body() != null ? resp.body().string() : null;
            }
        });

        if (response == null) {
            return null;
        }

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonObject distTags = json.getAsJsonObject("dist-tags");
        String latestVersion = distTags.get("latest").getAsString();

        JsonObject time = json.getAsJsonObject("time");
        String modifiedTime = time.get("modified").getAsString();

        LocalDateTime modified = LocalDateTime.parse(
                modifiedTime.substring(0, 19)
        );

        // Only include if updated within the configured time window
        if (modified.isBefore(LocalDateTime.now().minusHours(collectionHours))) {
            return null;
        }

        String description = json.has("description") && !json.get("description").isJsonNull()
                ? json.get("description").getAsString()
                : "";

        String homepage = json.has("homepage") && !json.get("homepage").isJsonNull()
                ? json.get("homepage").getAsString()
                : "https://www.npmjs.com/package/" + packageName;

        return NewsItem.builder()
                .title(String.format("%s v%s", packageName, latestVersion))
                .url(homepage)
                .source("npm")
                .category("Package")
                .publishedAt(modified)
                .description(description)
                .build();
    }
}
