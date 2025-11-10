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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects news from GitHub releases and trending repositories
 */
@Slf4j
@Component
public class GitHubCollector {

    private final OkHttpClient httpClient;
    private final RetryTemplate retryTemplate;

    @Value("${github.api.url}")
    private String githubApiUrl;

    @Value("${github.trending.repos}")
    private String trendingRepos;

    @Value("${github.trending.min.stars:100}")
    private int minStars;

    @Value("${news.collection.hours:24}")
    private int collectionHours;

    public GitHubCollector(OkHttpClient httpClient, RetryTemplate retryTemplate) {
        this.httpClient = httpClient;
        this.retryTemplate = retryTemplate;
    }

    /**
     * Collect recent releases from specified repositories
     */
    public List<NewsItem> collectReleases() {
        List<NewsItem> items = new ArrayList<>();
        String[] repos = trendingRepos.split(",");

        for (String repo : repos) {
            try {
                items.addAll(fetchReleasesForRepo(repo.trim()));
            } catch (Exception e) {
                log.error("Failed to fetch releases for {}: {}", repo, e.getMessage());
            }
        }

        log.info("Collected {} GitHub releases", items.size());
        return items;
    }

    /**
     * Collect trending repositories
     */
    public List<NewsItem> collectTrending() {
        List<NewsItem> items = new ArrayList<>();

        try {
            LocalDate cutoffDate = LocalDate.now().minusDays((collectionHours + 23) / 24);
            String dateStr = cutoffDate.format(DateTimeFormatter.ISO_DATE);

            String url = String.format(
                    "%s/search/repositories?q=language:javascript+language:typescript+created:>%s&sort=stars&order=desc&per_page=10",
                    githubApiUrl, dateStr
            );

            String response = retryTemplate.execute(context -> {
                Request request = new Request.Builder()
                        .url(url)
                        .header("Accept", "application/vnd.github.v3+json")
                        .build();

                try (Response resp = httpClient.newCall(request).execute()) {
                    if (!resp.isSuccessful()) {
                        throw new RuntimeException("GitHub API returned " + resp.code());
                    }
                    return resp.body() != null ? resp.body().string() : "{}";
                }
            });

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonArray repos = json.getAsJsonArray("items");

            for (JsonElement elem : repos) {
                JsonObject repoObj = elem.getAsJsonObject();
                int stars = repoObj.get("stargazers_count").getAsInt();

                if (stars >= minStars) {
                    items.add(NewsItem.builder()
                            .title(repoObj.get("full_name").getAsString())
                            .description(repoObj.has("description") && !repoObj.get("description").isJsonNull()
                                    ? repoObj.get("description").getAsString()
                                    : "")
                            .url(repoObj.get("html_url").getAsString())
                            .source("GitHub")
                            .category("Trending")
                            .score(stars)
                            .publishedAt(LocalDateTime.parse(
                                    repoObj.get("created_at").getAsString(),
                                    DateTimeFormatter.ISO_DATE_TIME
                            ))
                            .build());
                }
            }

        } catch (Exception e) {
            log.error("Failed to fetch trending repos: {}", e.getMessage());
        }

        log.info("Collected {} trending GitHub repos", items.size());
        return items;
    }

    private List<NewsItem> fetchReleasesForRepo(String repo) throws Exception {
        List<NewsItem> items = new ArrayList<>();

        // Try to find the full repo name (owner/repo)
        String repoPath = findRepoPath(repo);
        if (repoPath == null) {
            log.warn("Could not find repository for: {}", repo);
            return items;
        }

        String url = String.format("%s/repos/%s/releases?per_page=5", githubApiUrl, repoPath);

        String response = retryTemplate.execute(context -> {
            Request request = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

            try (Response resp = httpClient.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    if (resp.code() == 404) {
                        return "[]"; // Repo not found, return empty
                    }
                    throw new RuntimeException("GitHub API returned " + resp.code());
                }
                return resp.body() != null ? resp.body().string() : "[]";
            }
        });

        JsonArray releases = JsonParser.parseString(response).getAsJsonArray();

        for (JsonElement elem : releases) {
            JsonObject release = elem.getAsJsonObject();

            // Only include releases from the configured time window
            LocalDateTime publishedAt = LocalDateTime.parse(
                    release.get("published_at").getAsString(),
                    DateTimeFormatter.ISO_DATE_TIME
            );

            if (publishedAt.isAfter(LocalDateTime.now().minusHours(collectionHours))) {
                items.add(NewsItem.builder()
                        .title(String.format("%s %s", repoPath, release.get("tag_name").getAsString()))
                        .description(release.has("name") && !release.get("name").isJsonNull()
                                ? release.get("name").getAsString()
                                : "")
                        .url(release.get("html_url").getAsString())
                        .source("GitHub")
                        .category("Release")
                        .publishedAt(publishedAt)
                        .build());
            }
        }

        return items;
    }

    private String findRepoPath(String repoName) {
        // Common mappings for frontend frameworks
        String[][] mappings = {
                {"react", "facebook/react"},
                {"vue", "vuejs/core"},
                {"angular", "angular/angular"},
                {"svelte", "sveltejs/svelte"},
                {"next.js", "vercel/next.js"},
                {"remix", "remix-run/remix"}
        };

        for (String[] mapping : mappings) {
            if (mapping[0].equalsIgnoreCase(repoName)) {
                return mapping[1];
            }
        }

        // If it already looks like owner/repo, return as-is
        if (repoName.contains("/")) {
            return repoName;
        }

        return null;
    }
}
