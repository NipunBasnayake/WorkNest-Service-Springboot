package com.worknest.config;

import com.worknest.security.filter.StompJwtChannelInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    @Test
    void registersNativeStompEndpoint() {
        StompJwtChannelInterceptor interceptor = mock(StompJwtChannelInterceptor.class);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration endpoint = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(endpoint);
        when(endpoint.setAllowedOriginPatterns("http://localhost:5173", "https://worknest.example"))
                .thenReturn(endpoint);

        WebSocketConfig config = new WebSocketConfig(
                interceptor,
                "http://localhost:5173, https://worknest.example"
        );

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
        verify(endpoint).setAllowedOriginPatterns(aryEq(new String[]{
                "http://localhost:5173",
                "https://worknest.example"
        }));
    }
}
