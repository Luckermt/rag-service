package com.rag.rag_service.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.rag.rag_service.service.OllamaQuotaHandler;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.api-key}")
    private String apiKey;

    @Value("${webclient.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${webclient.read-timeout:30000}")
    private int readTimeout;

    @Bean
    public WebClient ollamaWebClient(OllamaQuotaHandler quotaHandler) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(readTimeout))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                            .addHandlerLast(new WriteTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                );

        return WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .filter(quotaHandler)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean(name = "downloadWebClient")
    public WebClient downloadWebClient(@Value("${rag.url-download.max-size:10485760}") long maxDownloadSize) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(readTimeout));

        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize((int) maxDownloadSize))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}