package ru.sportlife.mcp.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient sportlifeRestClient(RestClient.Builder builder, SportlifeProperties properties) {
        return applyDefaults(builder, properties).build();
    }

    static RestClient.Builder applyDefaults(RestClient.Builder builder, SportlifeProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("x-auth-token", properties.authToken())
                .defaultHeader("platform", properties.platform())
                .defaultHeader("version", properties.appVersion())
                .defaultHeader("user-agent", "SportLife/" + properties.appVersion());
    }
}
