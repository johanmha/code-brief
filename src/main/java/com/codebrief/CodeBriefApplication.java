package com.codebrief;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Code Brief - Your daily frontend news digest
 *
 * Collects news from GitHub, Reddit, Hacker News, Dev.to, RSS feeds, and npm
 * Uses Gemini AI to analyze and summarize
 * Delivers to Slack
 */
@SpringBootApplication
public class CodeBriefApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeBriefApplication.class, args);
    }
}
