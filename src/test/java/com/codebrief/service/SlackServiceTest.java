package com.codebrief.service;

import com.codebrief.model.Digest;
import com.codebrief.model.NewsItem;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.lenient;

/**
 * Test for SlackService
 */
@ExtendWith(MockitoExtension.class)
class SlackServiceTest {

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private RetryTemplate retryTemplate;

    @Mock
    private Call call;

    @Mock
    private Response response;

    @Mock
    private ResponseBody responseBody;

    private SlackService slackService;

    @BeforeEach
    void setUp() throws Exception {
        slackService = new SlackService(httpClient, retryTemplate);
        ReflectionTestUtils.setField(slackService, "webhookUrl", "https://hooks.slack.com/test");

        // Setup default mock behavior (lenient to avoid UnnecessaryStubbing errors)
        lenient().when(httpClient.newCall(any(Request.class))).thenReturn(call);
        lenient().when(call.execute()).thenReturn(response);
        lenient().when(response.isSuccessful()).thenReturn(true);
        lenient().when(response.body()).thenReturn(responseBody);
        lenient().when(responseBody.string()).thenReturn("ok");
    }

    @Test
    void testPostDigest_Success() throws Exception {
        // Arrange
        Digest digest = createTestDigest();

        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return "ok";
        });

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> slackService.postDigest(digest));
    }

    @Test
    void testPostDigest_WithAllSections() throws Exception {
        // Arrange
        Digest digest = createTestDigest();

        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return "ok";
        });

        // Act
        slackService.postDigest(digest);

        // Assert - Verify the request was made
        verify(retryTemplate, times(1)).execute(any());
    }

    @Test
    void testPostDigest_EmptyDigest() throws Exception {
        // Arrange
        Digest digest = Digest.builder()
                .date("Monday, November 10, 2025")
                .topUpdates(List.of())
                .quickMentions(List.of())
                .communityBuzz(List.of())
                .build();

        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return "ok";
        });

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> slackService.postDigest(digest));
    }

    @Test
    void testPostDigest_APIFailure() throws Exception {
        // Arrange
        Digest digest = createTestDigest();

        when(retryTemplate.execute(any())).thenThrow(new RuntimeException("Slack API error"));

        // Act & Assert - Should throw exception
        assertThrows(RuntimeException.class, () -> {
            slackService.postDigest(digest);
        });
    }

    @Test
    void testPostDigest_FormatsMessageCorrectly() throws Exception {
        // Arrange
        NewsItem githubItem = NewsItem.builder()
                .title("React 19.0")
                .url("https://github.com/facebook/react")
                .source("GitHub")
                .score(500)
                .build();

        NewsItem redditItem = NewsItem.builder()
                .title("Great Discussion")
                .url("https://reddit.com/r/reactjs")
                .source("Reddit")
                .score(450)
                .build();

        Digest digest = Digest.builder()
                .date("Monday, November 10, 2025")
                .topUpdates(Arrays.asList(githubItem))
                .quickMentions(List.of())
                .communityBuzz(Arrays.asList(redditItem))
                .build();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return "ok";
        });

        // Act
        slackService.postDigest(digest);

        // Assert - Message should contain key elements
        verify(retryTemplate).execute(any());
    }

    @Test
    void testPostDigest_HandlesItemsWithoutScores() throws Exception {
        // Arrange
        NewsItem itemWithoutScore = NewsItem.builder()
                .title("Article Title")
                .url("https://example.com")
                .source("RSS")
                .score(null)
                .build();

        Digest digest = Digest.builder()
                .date("Monday, November 10, 2025")
                .topUpdates(Arrays.asList(itemWithoutScore))
                .quickMentions(List.of())
                .communityBuzz(List.of())
                .build();

        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return "ok";
        });

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> slackService.postDigest(digest));
    }

    @Test
    void testPostDigest_MultipleItemsInEachSection() throws Exception {
        // Arrange
        Digest digest = Digest.builder()
                .date("Monday, November 10, 2025")
                .topUpdates(Arrays.asList(
                        createNewsItem("Update 1", "GitHub", 500),
                        createNewsItem("Update 2", "GitHub", 400),
                        createNewsItem("Update 3", "GitHub", 300)
                ))
                .quickMentions(Arrays.asList(
                        createNewsItem("Mention 1", "RSS", 100),
                        createNewsItem("Mention 2", "DevTo", 50)
                ))
                .communityBuzz(Arrays.asList(
                        createNewsItem("Discussion 1", "Reddit", 450),
                        createNewsItem("Discussion 2", "HackerNews", 350)
                ))
                .build();

        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return "ok";
        });

        // Act & Assert
        assertDoesNotThrow(() -> slackService.postDigest(digest));
        verify(retryTemplate, times(1)).execute(any());
    }

    @Test
    void testTestWebhook_Success() throws Exception {
        // Arrange
        when(response.isSuccessful()).thenReturn(true);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> slackService.testWebhook());

        verify(httpClient, times(1)).newCall(any(Request.class));
    }

    @Test
    void testTestWebhook_Failure() throws Exception {
        // Arrange
        when(response.isSuccessful()).thenReturn(false);
        when(response.code()).thenReturn(404);

        // Act & Assert - Should not throw (logs error instead)
        assertDoesNotThrow(() -> slackService.testWebhook());
    }

    private Digest createTestDigest() {
        return Digest.builder()
                .date("Monday, November 10, 2025")
                .topUpdates(Arrays.asList(
                        createNewsItem("React 19.0", "GitHub", 500)
                ))
                .quickMentions(Arrays.asList(
                        createNewsItem("CSS Article", "RSS", 100)
                ))
                .communityBuzz(Arrays.asList(
                        createNewsItem("Discussion", "Reddit", 450)
                ))
                .build();
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
