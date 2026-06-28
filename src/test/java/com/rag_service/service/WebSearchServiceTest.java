package com.rag_service.service;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.rag.rag_service.service.WebSearchService;

@ExtendWith(MockitoExtension.class)
class WebSearchServiceTest {

    private WebSearchService webSearchService;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        webSearchService = new WebSearchService(webClientBuilder);
        ReflectionTestUtils.setField(webSearchService, "enabled", true);
        ReflectionTestUtils.setField(webSearchService, "searxngUrl", "http://searxng:8080");

        // Настраиваем цепочку WebClient
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    // @Test
    // void search_shouldReturnResultsWhenEnabled() {
    //     // given
    //     Map<String, Object> response = Map.of(
    //             "results", List.of(
    //                     Map.of("title", "Title1", "content", "Content1"),
    //                     Map.of("title", "Title2", "content", "Content2")
    //             )
    //     );
    //     when(responseSpec.bodyToMono(Map.class)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));

    //     // when
    //     String result = webSearchService.search("test query");

    //     // then
    //     assertThat(result).contains("Title1: Content1");
    //     assertThat(result).contains("Title2: Content2");
    // }

    @Test
    void search_shouldReturnEmptyWhenDisabled() {
        // given
        ReflectionTestUtils.setField(webSearchService, "enabled", false);

        // when
        String result = webSearchService.search("test");

        // then
        assertThat(result).isEmpty();
        verify(webClientBuilder, never()).build();
    }

    @Test
    void search_shouldReturnEmptyOnException() {
        // given
        when(responseSpec.bodyToMono(Map.class)).thenThrow(new RuntimeException("Network error"));

        // when
        String result = webSearchService.search("test");

        // then
        assertThat(result).isEmpty();
    }
}