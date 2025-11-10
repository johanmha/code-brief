package com.codebrief.service;

import com.codebrief.collector.*;
import com.codebrief.model.NewsItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Orchestrates collection of news from all sources
 */
@Slf4j
@Service
public class NewsCollectorService {

    private final GitHubCollector gitHubCollector;
    private final RedditCollector redditCollector;
    private final HackerNewsCollector hackerNewsCollector;
    private final DevToCollector devToCollector;
    private final RSSCollector rssCollector;
    private final NpmCollector npmCollector;

    private final ExecutorService executorService;

    public NewsCollectorService(
            GitHubCollector gitHubCollector,
            RedditCollector redditCollector,
            HackerNewsCollector hackerNewsCollector,
            DevToCollector devToCollector,
            RSSCollector rssCollector,
            NpmCollector npmCollector
    ) {
        this.gitHubCollector = gitHubCollector;
        this.redditCollector = redditCollector;
        this.hackerNewsCollector = hackerNewsCollector;
        this.devToCollector = devToCollector;
        this.rssCollector = rssCollector;
        this.npmCollector = npmCollector;
        this.executorService = Executors.newFixedThreadPool(6);
    }

    /**
     * Collect news from all sources in parallel
     */
    public List<NewsItem> collectAllNews() {
        log.info("Starting news collection from all sources...");

        List<Future<List<NewsItem>>> futures = new ArrayList<>();

        // Submit all collection tasks in parallel
        futures.add(executorService.submit(() -> {
            List<NewsItem> items = new ArrayList<>();
            items.addAll(gitHubCollector.collectReleases());
            items.addAll(gitHubCollector.collectTrending());
            return items;
        }));

        futures.add(executorService.submit(redditCollector::collectPosts));
        futures.add(executorService.submit(hackerNewsCollector::collectStories));
        futures.add(executorService.submit(devToCollector::collectArticles));
        futures.add(executorService.submit(rssCollector::collectArticles));
        futures.add(executorService.submit(npmCollector::collectPackageInfo));

        // Collect results
        List<NewsItem> allNews = new ArrayList<>();

        for (Future<List<NewsItem>> future : futures) {
            try {
                List<NewsItem> items = future.get(60, TimeUnit.SECONDS);
                allNews.addAll(items);
            } catch (TimeoutException e) {
                log.error("Collection task timed out");
            } catch (Exception e) {
                log.error("Collection task failed: {}", e.getMessage());
            }
        }

        log.info("Collected {} total news items from all sources", allNews.size());
        return allNews;
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}
