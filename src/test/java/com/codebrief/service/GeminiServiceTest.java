package com.codebrief.service;

import com.codebrief.model.Digest;
import com.codebrief.model.NewsItem;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test for GeminiService
 */
@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private RetryTemplate retryTemplate;

    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        geminiService = new GeminiService(httpClient, retryTemplate);

        // Set required properties
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(geminiService, "apiUrl", "https://test-api.com");
        ReflectionTestUtils.setField(geminiService, "maxTokens", 2048);
        ReflectionTestUtils.setField(geminiService, "temperature", 0.7);
    }

    @Test
    void testProcessNewsItems_EmptyList() {
        // Act
        Digest result = geminiService.processNewsItems(List.of());

        // Assert
        assertNotNull(result);
        assertNotNull(result.getDate());
        assertTrue(result.getTopUpdates().isEmpty());
        assertTrue(result.getQuickMentions().isEmpty());
        assertTrue(result.getCommunityBuzz().isEmpty());
    }

    @Test
    void testProcessNewsItems_WithValidResponse() throws Exception {
        // Arrange
        List<NewsItem> newsItems = createTestNewsItems();

        // This is what callGeminiAPI returns (the extracted text from the API response)
        String extractedJsonText = "{\"topUpdates\": [1, 2], \"quickMentions\": [3], \"communityBuzz\": [4]}";

        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return extractedJsonText;
        });

        // Act
        Digest result = geminiService.processNewsItems(newsItems);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getDate());
        assertEquals(2, result.getTopUpdates().size());
        assertEquals(1, result.getQuickMentions().size());
        assertEquals(1, result.getCommunityBuzz().size());
    }

    @Test
    void testProcessNewsItems_APIFailure_UsesFallback() throws Exception {
        // Arrange
        List<NewsItem> newsItems = createTestNewsItems();

        when(retryTemplate.execute(any())).thenThrow(new RuntimeException("API Error"));

        // Act
        Digest result = geminiService.processNewsItems(newsItems);

        // Assert - Should use fallback logic
        assertNotNull(result);
        assertNotNull(result.getDate());
        assertNotNull(result.getTopUpdates());
        assertNotNull(result.getQuickMentions());
        assertNotNull(result.getCommunityBuzz());
    }

    @Test
    void testProcessNewsItems_InvalidJSONResponse_UsesFallback() throws Exception {
        // Arrange
        List<NewsItem> newsItems = createTestNewsItems();

        // This is what callGeminiAPI would return (extracted text that is not valid JSON)
        String invalidJsonText = "This is not valid JSON";

        when(retryTemplate.execute(any())).thenReturn(invalidJsonText);

        // Act
        Digest result = geminiService.processNewsItems(newsItems);

        // Assert - Should use fallback logic
        assertNotNull(result);
        assertNotNull(result.getDate());
    }

    @Test
    void testProcessNewsItems_FallbackSortsByScore() throws Exception {
        // Arrange
        List<NewsItem> newsItems = Arrays.asList(
                createNewsItem("Low score", "test", 10),
                createNewsItem("High score", "test", 100),
                createNewsItem("Medium score", "test", 50)
        );

        when(retryTemplate.execute(any())).thenThrow(new RuntimeException("API Error"));

        // Act
        Digest result = geminiService.processNewsItems(newsItems);

        // Assert - Items should be sorted by score (highest first)
        assertNotNull(result);
        if (!result.getTopUpdates().isEmpty()) {
            assertEquals("High score", result.getTopUpdates().get(0).getTitle());
        }
    }

    @Test
    void testProcessNewsItems_HandlesNullScores() throws Exception {
        // Arrange
        List<NewsItem> newsItems = Arrays.asList(
                createNewsItem("No score", "test", null),
                createNewsItem("With score", "test", 100)
        );

        when(retryTemplate.execute(any())).thenThrow(new RuntimeException("API Error"));

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> {
            Digest result = geminiService.processNewsItems(newsItems);
            assertNotNull(result);
        });
    }

    private List<NewsItem> createTestNewsItems() {
        return Arrays.asList(
                createNewsItem("React 19.0 Released", "GitHub", 500),
                createNewsItem("Vue 3.5 Update", "GitHub", 300),
                createNewsItem("CSS Tricks Article", "RSS", 100),
                createNewsItem("Reddit Discussion", "Reddit", 450)
        );
    }

    private NewsItem createNewsItem(String title, String source, Integer score) {
        return NewsItem.builder()
                .title(title)
                .url("https://example.com/" + title.toLowerCase().replace(" ", "-"))
                .source(source)
                .category("Test")
                .score(score)
                .publishedAt(LocalDateTime.now())
                .description("Test description")
                .build();
    }
}
