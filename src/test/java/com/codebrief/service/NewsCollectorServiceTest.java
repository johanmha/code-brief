package com.codebrief.service;

import com.codebrief.collector.*;
import com.codebrief.model.NewsItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for NewsCollectorService
 */
@ExtendWith(MockitoExtension.class)
class NewsCollectorServiceTest {

    @Mock
    private GitHubCollector gitHubCollector;

    @Mock
    private RedditCollector redditCollector;

    @Mock
    private HackerNewsCollector hackerNewsCollector;

    @Mock
    private DevToCollector devToCollector;

    @Mock
    private RSSCollector rssCollector;

    @Mock
    private NpmCollector npmCollector;

    private NewsCollectorService newsCollectorService;

    @BeforeEach
    void setUp() {
        newsCollectorService = new NewsCollectorService(
                gitHubCollector,
                redditCollector,
                hackerNewsCollector,
                devToCollector,
                rssCollector,
                npmCollector
        );
    }

    @Test
    void testCollectAllNews_Success() {
        // Arrange
        NewsItem githubItem = createNewsItem("GitHub Release", "github");
        NewsItem redditItem = createNewsItem("Reddit Post", "reddit");
        NewsItem hnItem = createNewsItem("HN Story", "hackernews");

        when(gitHubCollector.collectReleases()).thenReturn(Arrays.asList(githubItem));
        when(gitHubCollector.collectTrending()).thenReturn(List.of());
        when(redditCollector.collectPosts()).thenReturn(Arrays.asList(redditItem));
        when(hackerNewsCollector.collectStories()).thenReturn(Arrays.asList(hnItem));
        when(devToCollector.collectArticles()).thenReturn(List.of());
        when(rssCollector.collectArticles()).thenReturn(List.of());
        when(npmCollector.collectPackageInfo()).thenReturn(List.of());

        // Act
        List<NewsItem> result = newsCollectorService.collectAllNews();

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(githubItem));
        assertTrue(result.contains(redditItem));
        assertTrue(result.contains(hnItem));

        verify(gitHubCollector).collectReleases();
        verify(gitHubCollector).collectTrending();
        verify(redditCollector).collectPosts();
        verify(hackerNewsCollector).collectStories();
        verify(devToCollector).collectArticles();
        verify(rssCollector).collectArticles();
        verify(npmCollector).collectPackageInfo();
    }

    @Test
    void testCollectAllNews_EmptyResult() {
        // Arrange
        when(gitHubCollector.collectReleases()).thenReturn(List.of());
        when(gitHubCollector.collectTrending()).thenReturn(List.of());
        when(redditCollector.collectPosts()).thenReturn(List.of());
        when(hackerNewsCollector.collectStories()).thenReturn(List.of());
        when(devToCollector.collectArticles()).thenReturn(List.of());
        when(rssCollector.collectArticles()).thenReturn(List.of());
        when(npmCollector.collectPackageInfo()).thenReturn(List.of());

        // Act
        List<NewsItem> result = newsCollectorService.collectAllNews();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCollectAllNews_OneCollectorFails() {
        // Arrange
        NewsItem githubItem = createNewsItem("GitHub Release", "github");

        when(gitHubCollector.collectReleases()).thenReturn(Arrays.asList(githubItem));
        when(gitHubCollector.collectTrending()).thenReturn(List.of());
        when(redditCollector.collectPosts()).thenThrow(new RuntimeException("Reddit API error"));
        when(hackerNewsCollector.collectStories()).thenReturn(List.of());
        when(devToCollector.collectArticles()).thenReturn(List.of());
        when(rssCollector.collectArticles()).thenReturn(List.of());
        when(npmCollector.collectPackageInfo()).thenReturn(List.of());

        // Act
        List<NewsItem> result = newsCollectorService.collectAllNews();

        // Assert - Should still collect from other sources
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.contains(githubItem));
    }

    @Test
    void testCollectAllNews_MultipleItemsFromSameSource() {
        // Arrange
        NewsItem release1 = createNewsItem("React 19.0", "github");
        NewsItem release2 = createNewsItem("Vue 3.5", "github");

        when(gitHubCollector.collectReleases()).thenReturn(Arrays.asList(release1, release2));
        when(gitHubCollector.collectTrending()).thenReturn(List.of());
        when(redditCollector.collectPosts()).thenReturn(List.of());
        when(hackerNewsCollector.collectStories()).thenReturn(List.of());
        when(devToCollector.collectArticles()).thenReturn(List.of());
        when(rssCollector.collectArticles()).thenReturn(List.of());
        when(npmCollector.collectPackageInfo()).thenReturn(List.of());

        // Act
        List<NewsItem> result = newsCollectorService.collectAllNews();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(release1));
        assertTrue(result.contains(release2));
    }

    private NewsItem createNewsItem(String title, String source) {
        return NewsItem.builder()
                .title(title)
                .url("https://example.com/" + title.toLowerCase().replace(" ", "-"))
                .source(source)
                .category("Test")
                .score(100)
                .publishedAt(LocalDateTime.now())
                .description("Test description")
                .build();
    }
}
