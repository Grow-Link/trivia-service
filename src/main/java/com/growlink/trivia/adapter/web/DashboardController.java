package com.growlink.trivia.adapter.web;

import com.growlink.trivia.adapter.security.TokenService;
import com.growlink.trivia.application.DashboardService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// HU-24: solo el admin ve las metricas de concurrencia y tiempo real
@RestController
@RequestMapping("/api/metricas")
public class DashboardController {

    private final DashboardService dashboardService;
    private final TokenService tokenService;

    public DashboardController(DashboardService dashboardService, TokenService tokenService) {
        this.dashboardService = dashboardService;
        this.tokenService = tokenService;
    }

    @GetMapping("/dashboard")
    public DashboardService.Dashboard dashboard(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) Integer ultimosMinutos) {
        tokenService.exigirAdmin(authorization);
        if (ultimosMinutos != null && ultimosMinutos < 1) {
            throw new IllegalArgumentException("ultimosMinutos debe ser 1 o mas");
        }
        return dashboardService.obtener(ultimosMinutos);
    }
}
