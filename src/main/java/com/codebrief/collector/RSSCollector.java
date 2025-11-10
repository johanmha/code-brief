package com.codebrief.collector;

import com.codebrief.model.NewsItem;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects articles from RSS feeds
 */
@Slf4j
@Component
public class RSSCollector {

    private final OkHttpClient httpClient;
    private final RetryTemplate retryTemplate;

    @Value("${rss.feeds}")
    private String rssFeeds;

    @Value("${news.collection.hours:24}")
    private int collectionHours;

    public RSSCollector(OkHttpClient httpClient, RetryTemplate retryTemplate) {
        this.httpClient = httpClient;
        this.retryTemplate = retryTemplate;
    }

    /**
     * Collect articles from RSS feeds
     */
    public List<NewsItem> collectArticles() {
        List<NewsItem> items = new ArrayList<>();
        String[] feeds = rssFeeds.split(",");

        for (String feedUrl : feeds) {
            try {
                items.addAll(fetchFeed(feedUrl.trim()));
            } catch (Exception e) {
                log.error("Failed to fetch RSS feed {}: {}", feedUrl, e.getMessage());
            }
        }

        log.info("Collected {} RSS articles", items.size());
        return items;
    }

    private List<NewsItem> fetchFeed(String feedUrl) throws Exception {
        List<NewsItem> items = new ArrayList<>();

        String response = retryTemplate.execute(context -> {
            Request request = new Request.Builder()
                    .url(feedUrl)
                    .build();

            try (Response resp = httpClient.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    throw new RuntimeException("RSS feed returned " + resp.code());
                }
                return resp.body() != null ? resp.body().string() : "";
            }
        });

        // Parse RSS feed
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed;

        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(response.getBytes()))) {
            feed = input.build(reader);
        }

        String feedTitle = feed.getTitle();

        // Get recent entries from the configured time window
        LocalDateTime cutoff = LocalDateTime.now().minusHours(collectionHours);

        for (SyndEntry entry : feed.getEntries()) {
            try {
                LocalDateTime publishedAt = entry.getPublishedDate() != null
                        ? LocalDateTime.ofInstant(entry.getPublishedDate().toInstant(), ZoneId.systemDefault())
                        : LocalDateTime.now();

                if (publishedAt.isAfter(cutoff)) {
                    String description = entry.getDescription() != null
                            ? stripHtml(entry.getDescription().getValue())
                            : "";

                    items.add(NewsItem.builder()
                            .title(entry.getTitle())
                            .url(entry.getLink())
                            .source("RSS")
                            .category(feedTitle)
                            .publishedAt(publishedAt)
                            .description(truncate(description, 200))
                            .build());
                }
            } catch (Exception e) {
                log.warn("Failed to parse RSS entry: {}", e.getMessage());
            }
        }

        return items;
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]*>", "").trim();
    }

    private String truncate(String text, int length) {
        if (text == null || text.length() <= length) {
            return text;
        }
        return text.substring(0, length) + "...";
    }
}
