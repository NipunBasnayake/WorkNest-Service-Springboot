package com.worknest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "tenantProvisioningExecutor")
    public Executor tenantProvisioningExecutor(
            @Value("${app.onboarding.provisioning.executor.core-pool-size:2}") int corePoolSize,
            @Value("${app.onboarding.provisioning.executor.max-pool-size:8}") int maxPoolSize,
            @Value("${app.onboarding.provisioning.executor.queue-capacity:200}") int queueCapacity,
            @Value("${app.onboarding.provisioning.executor.keep-alive-seconds:60}") int keepAliveSeconds) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("tenant-provision-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "emailExecutor")
    public Executor emailExecutor(
            @Value("${app.email.executor.core-pool-size:2}") int corePoolSize,
            @Value("${app.email.executor.max-pool-size:10}") int maxPoolSize,
            @Value("${app.email.executor.queue-capacity:500}") int queueCapacity,
            @Value("${app.email.executor.keep-alive-seconds:60}") int keepAliveSeconds) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("email-dispatch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
