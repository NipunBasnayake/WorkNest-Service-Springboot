package com.worknest.config;

import com.worknest.security.filter.StompJwtChannelInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    @Test
    void configuresHeartbeatsWithTheFrameworkMessageBrokerScheduler() {
        StompJwtChannelInterceptor interceptor = mock(StompJwtChannelInterceptor.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TaskScheduler> schedulerProvider = mock(ObjectProvider.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        SimpleBrokerRegistration broker = mock(SimpleBrokerRegistration.class);
        when(schedulerProvider.getObject()).thenReturn(scheduler);
        when(registry.enableSimpleBroker("/topic", "/queue")).thenReturn(broker);
        when(broker.setTaskScheduler(scheduler)).thenReturn(broker);
        when(broker.setHeartbeatValue(aryEq(new long[]{10_000, 10_000}))).thenReturn(broker);

        WebSocketConfig config = new WebSocketConfig(interceptor, schedulerProvider, "http://localhost:5173");

        config.configureMessageBroker(registry);

        verify(broker).setTaskScheduler(scheduler);
        verify(broker).setHeartbeatValue(aryEq(new long[]{10_000, 10_000}));
        verify(registry).setApplicationDestinationPrefixes("/app");
        verify(registry).setUserDestinationPrefix("/user");
    }

    @Test
    void registersNativeAndSockJsStompEndpoints() {
        StompJwtChannelInterceptor interceptor = mock(StompJwtChannelInterceptor.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TaskScheduler> schedulerProvider = mock(ObjectProvider.class);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration nativeEndpoint = mock(StompWebSocketEndpointRegistration.class);
        StompWebSocketEndpointRegistration sockJsEndpoint = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(nativeEndpoint);
        when(registry.addEndpoint("/ws-sockjs")).thenReturn(sockJsEndpoint);
        when(nativeEndpoint.setAllowedOriginPatterns("http://localhost:5173", "https://worknest.example"))
                .thenReturn(nativeEndpoint);
        when(sockJsEndpoint.setAllowedOriginPatterns("http://localhost:5173", "https://worknest.example"))
                .thenReturn(sockJsEndpoint);

        WebSocketConfig config = new WebSocketConfig(
                interceptor,
                schedulerProvider,
                "http://localhost:5173, https://worknest.example"
        );

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
        verify(nativeEndpoint).setAllowedOriginPatterns(aryEq(new String[]{
                "http://localhost:5173",
                "https://worknest.example"
        }));
        verify(registry).addEndpoint("/ws-sockjs");
        verify(sockJsEndpoint).setAllowedOriginPatterns(aryEq(new String[]{
                "http://localhost:5173",
                "https://worknest.example"
        }));
        verify(sockJsEndpoint).withSockJS();
    }
}
