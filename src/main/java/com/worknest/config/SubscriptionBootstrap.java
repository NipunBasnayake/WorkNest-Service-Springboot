package com.worknest.config;

import com.worknest.master.service.SubscriptionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SubscriptionBootstrap implements ApplicationRunner {

    private final SubscriptionService subscriptionService;

    public SubscriptionBootstrap(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        subscriptionService.bootstrapDefaults();
    }
}
