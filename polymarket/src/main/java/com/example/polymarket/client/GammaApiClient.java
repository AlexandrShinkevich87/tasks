package com.example.polymarket.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class GammaApiClient {

    private final RestClient restClient;

    public GammaApiClient(@Value("${polymarket.gamma-api-url}") String gammaApiUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(gammaApiUrl)
                .build();
    }

    public List<GammaMarketDto> getActiveMarkets(int limit) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/markets")
                        .queryParam("active", true)
                        .queryParam("closed", false)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public GammaMarketDto getMarketBySlug(String slug) {
        return restClient.get()
                .uri("/markets/slug/{slug}", slug)
                .retrieve()
                .body(GammaMarketDto.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GammaMarketDto {
        private String question;
        private String slug;
        private Boolean active;

        @JsonProperty("clobTokenIds")
        private String clobTokenIds;

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }

        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }

        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }

        public String getClobTokenIds() { return clobTokenIds; }
        public void setClobTokenIds(String clobTokenIds) { this.clobTokenIds = clobTokenIds; }
    }
}