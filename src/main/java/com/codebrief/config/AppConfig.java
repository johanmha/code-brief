package com.codebrief.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Main application configuration
 */
@Configuration
@EnableRetry
public class AppConfig {

    @Value("${http.connection.timeout:30}")
    private int connectionTimeout;

    @Value("${http.read.timeout:60}")
    private int readTimeout;

    @Value("${http.max.retries:3}")
    private int maxRetries;

    @Value("${http.retry.delay:2000}")
    private long retryDelay;

    /**
     * Configure OkHttp client with timeouts and connection pooling
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(connectionTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .build();
    }

    /**
     * Configure retry template for failed operations
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxRetries);
        retryTemplate.setRetryPolicy(retryPolicy);

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(retryDelay);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
