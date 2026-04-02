package com.example.polymarket.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "polymarket")
@Getter
@Setter
public class PolymarketProperties {
    private String wsUrl;
    private String gammaApiUrl;
    private SelectionMode marketSelectionMode = SelectionMode.CONFIG;
    private int autoTrackCount = 3;
    private Duration reconnectDelay = Duration.ofSeconds(5);
    private boolean useRedis = false;
    private List<String> trackedMarkets = new ArrayList<>();

    public enum SelectionMode {
        CONFIG,
        AUTO_TOP_ACTIVE
    }
}
