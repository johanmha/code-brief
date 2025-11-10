package com.codebrief.service;

import com.codebrief.model.Digest;
import com.codebrief.model.NewsItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Main runner that executes the digest generation and delivery
 */
@Slf4j
@Component
public class DigestRunner implements CommandLineRunner {

    private final NewsCollectorService newsCollectorService;
    private final GeminiService geminiService;
    private final SlackService slackService;

    public DigestRunner(
            NewsCollectorService newsCollectorService,
            GeminiService geminiService,
            SlackService slackService
    ) {
        this.newsCollectorService = newsCollectorService;
        this.geminiService = geminiService;
        this.slackService = slackService;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("═══════════════════════════════════════");
        log.info("  Code Brief - Daily Digest Generation");
        log.info("═══════════════════════════════════════");

        try {
            // Step 1: Collect news from all sources
            log.info("\n[1/3] Collecting news from all sources...");
            List<NewsItem> newsItems = newsCollectorService.collectAllNews();

            if (newsItems.isEmpty()) {
                log.warn("No news items collected. Exiting...");
                return;
            }

            log.info("Collected {} news items", newsItems.size());

            // Step 2: Process with Gemini AI
            log.info("\n[2/3] Processing news with Gemini AI...");
            Digest digest = geminiService.processNewsItems(newsItems);

            log.info("Generated digest with:");
            log.info("  - {} top updates", digest.getTopUpdates().size());
            log.info("  - {} quick mentions", digest.getQuickMentions().size());
            log.info("  - {} community buzz items", digest.getCommunityBuzz().size());

            // Step 3: Post to Slack
            log.info("\n[3/3] Posting digest to Slack...");
            slackService.postDigest(digest);

            log.info("\n═══════════════════════════════════════");
            log.info("  ✓ Digest delivered successfully!");
            log.info("═══════════════════════════════════════");

        } catch (Exception e) {
            log.error("\n═══════════════════════════════════════");
            log.error("  ✗ Digest generation failed!");
            log.error("  Error: {}", e.getMessage());
            log.error("═══════════════════════════════════════");
            throw e;
        } finally {
            // Cleanup
            newsCollectorService.shutdown();
        }
    }
}
