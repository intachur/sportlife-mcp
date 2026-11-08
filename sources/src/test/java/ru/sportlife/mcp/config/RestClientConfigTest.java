package ru.sportlife.mcp.config;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientConfigTest {

    @Test
    void appliesSportlifeHeaders() {
        SportlifeProperties properties = new SportlifeProperties(
                "https://sync.sportlifeclub.ru",
                "token",
                "6",
                "ios",
                "3.9.10",
                "Europe/Moscow",
                Duration.ofSeconds(5),
                Duration.ofSeconds(15));

        RestClient.Builder builder = RestClientConfig.applyDefaults(RestClient.builder(), properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(requestTo("https://sync.sportlifeclub.ru/handh/api/v1.8/club_card"))
                .andExpect(header("x-auth-token", "token"))
                .andExpect(header("platform", "ios"))
                .andExpect(header("version", "3.9.10"))
                .andExpect(header("user-agent", "SportLife/3.9.10"))
                .andRespond(withSuccess("{\"code\":1,\"message\":\"\",\"data\":{}}", MediaType.APPLICATION_JSON));

        restClient.get().uri("/handh/api/v1.8/club_card").retrieve().body(String.class);

        server.verify();
    }
}
