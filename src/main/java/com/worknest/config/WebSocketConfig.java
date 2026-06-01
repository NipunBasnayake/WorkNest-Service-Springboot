package com.worknest.config;

import com.worknest.security.filter.StompJwtChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketConfig.class);

    private final StompJwtChannelInterceptor stompJwtChannelInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(
            StompJwtChannelInterceptor stompJwtChannelInterceptor,
            @Value("${app.websocket.allowed-origins:http://localhost:3000,http://localhost:5173}") String allowedOriginsRaw) {
        this.stompJwtChannelInterceptor = stompJwtChannelInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    /**
     * Spring-managed task scheduler for the STOMP broker heartbeat.
     * Managed by Spring's lifecycle so it is gracefully shut down on context close,
     * preventing stale executor threads and the recurring start/stop pattern.
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler brokerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("simple-broker-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        return scheduler;
    }

    /**
     * Spring-managed executor for the clientInboundChannel.
     * A larger pool + caller-runs policy prevents ExecutorSubscribableChannel
     * rejections when DB lookups inside the STOMP interceptor block temporarily.
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor wsClientInboundExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(20);
        exec.setMaxPoolSize(100);
        exec.setQueueCapacity(10_000);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix("ws-inbound-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        /* Caller-runs: if the queue is full, the client thread handles the message
           instead of throwing a RejectedExecutionException. */
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return exec;
    }

    /**
     * Spring-managed executor for the clientOutboundChannel.
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor wsClientOutboundExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(20);
        exec.setMaxPoolSize(100);
        exec.setQueueCapacity(10_000);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix("ws-outbound-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return exec;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(brokerTaskScheduler())
                .setHeartbeatValue(new long[]{10_000, 10_000});
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompJwtChannelInterceptor);
        registration.taskExecutor(wsClientInboundExecutor());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(wsClientOutboundExecutor());
    }
}
