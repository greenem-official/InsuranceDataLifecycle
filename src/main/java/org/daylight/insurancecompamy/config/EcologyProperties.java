package org.daylight.insurancecompamy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ecology")
@Getter
@Setter
public class EcologyProperties {
    private double maxSizeMb = 1024.0;
    private int staleDays = 365;
    private Weights weights = new Weights();

    @Getter
    @Setter
    public static class Weights {
        private double volume = 0.20;
        private double duplicates = 0.30;
        private double stale = 0.25;
        private double quality = 0.25;
    }
}
