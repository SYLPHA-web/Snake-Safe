package com.snakesafe.alert;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.snakesafe.auth.AuthPrincipal;
import com.snakesafe.auth.Role;
import com.snakesafe.config.RateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Works for guests too: nobody is ever blocked from asking for help. */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService service;
    private final RateLimiter limiter;

    public AlertController(AlertService service, RateLimiter limiter) {
        this.service = service;
        this.limiter = limiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertView create(@Valid @RequestBody AlertRequest request, @AuthenticationPrincipal AuthPrincipal me,
            HttpServletRequest http) {
        limiter.check("alert:" + http.getRemoteAddr(), 10, Duration.ofMinutes(10));
        Long userId = me != null && me.role() == Role.USER ? me.id() : null;
        return service.create(request, userId);
    }

    @GetMapping("/{id}")
    public AlertView get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/{id}/cancel")
    public AlertView cancel(@PathVariable String id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/arrived")
    public AlertView arrived(@PathVariable String id) {
        return service.arrived(id);
    }
}
