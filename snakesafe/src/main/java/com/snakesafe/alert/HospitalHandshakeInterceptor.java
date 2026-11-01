package com.snakesafe.alert;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.snakesafe.auth.AuthPrincipal;
import com.snakesafe.auth.JwtService;
import com.snakesafe.auth.Role;
import com.snakesafe.staff.StaffRepository;

/** Browsers cannot set headers on a WebSocket, so staff pass their login token as ?token=. */
@Component
public class HospitalHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwt;
    private final StaffRepository staff;

    public HospitalHandshakeInterceptor(JwtService jwt, StaffRepository staff) {
        this.jwt = jwt;
        this.staff = staff;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        long requested;
        try {
            requested = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        } catch (NumberFormatException e) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        AuthPrincipal p = token == null ? null : jwt.parse(token).orElse(null);
        boolean allowed = p != null && p.role() == Role.STAFF && staff.findById(p.id())
                .filter(s -> s.active && s.hospitalId != null && s.hospitalId == requested).isPresent();
        if (!allowed) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(HospitalSocketHandler.HOSPITAL_ATTRIBUTE, requested);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Exception exception) {
    }
}
