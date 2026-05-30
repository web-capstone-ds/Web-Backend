package com.ds.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient aiRestClient(@Value("${ai-server.base-url}") String baseUrl,
                            @Value("${ai-server.api-timeout-ms:30000}") long timeoutMs) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory(timeoutMs, timeoutMs)).build();
    }

    @Bean
    RestClient aiQueryRestClient(@Value("${ai-server.base-url}") String baseUrl,
                                 @Value("${ai-server.api-timeout-ms:30000}") long connectTimeoutMs,
                                 @Value("${ai-server.query-timeout-ms:130000}") long queryTimeoutMs) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory(connectTimeoutMs, queryTimeoutMs)).build();
    }

    private SimpleClientHttpRequestFactory requestFactory(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return requestFactory;
    }
}
