package com.weili.example.starter;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * @author luying
 * @description: curl <a href="http://localhost:8080/actuator/health">...</a>
 * @date 2025/5/28 13:30
 */
@Component
public class HealthController implements HealthIndicator {

    @Override
    public Health health() {
        return Health.up()
                .withDetail("custom", "OK")
                .build();
    }
}
