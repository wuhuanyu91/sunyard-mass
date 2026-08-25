package com.sunyard.llm.mas.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient 配置：非阻塞转发推理引擎（附录 E.5 / G.5）。
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient masWebClient(MasProperties props) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(props.getBackend().getTimeout())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
