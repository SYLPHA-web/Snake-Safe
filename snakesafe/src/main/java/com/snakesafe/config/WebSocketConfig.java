package com.snakesafe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.snakesafe.alert.AlertSocketHandler;
import com.snakesafe.alert.HospitalHandshakeInterceptor;
import com.snakesafe.alert.HospitalSocketHandler;

/** No allowed origins are listed, so only same-origin pages can open these sockets. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AlertSocketHandler alertHandler;
    private final HospitalSocketHandler hospitalHandler;
    private final HospitalHandshakeInterceptor hospitalAuth;

    public WebSocketConfig(AlertSocketHandler alertHandler, HospitalSocketHandler hospitalHandler,
            HospitalHandshakeInterceptor hospitalAuth) {
        this.alertHandler = alertHandler;
        this.hospitalHandler = hospitalHandler;
        this.hospitalAuth = hospitalAuth;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertHandler, "/ws/alerts/*");
        registry.addHandler(hospitalHandler, "/ws/hospital/*").addInterceptors(hospitalAuth);
    }
}
