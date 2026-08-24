package com.example.panel.controller;

import com.example.panel.service.ProductionReadinessService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/production-readiness")
@PreAuthorize("hasAuthority('PAGE_SETTINGS')")
public class ProductionReadinessApiController {

    private final ProductionReadinessService productionReadinessService;

    public ProductionReadinessApiController(ProductionReadinessService productionReadinessService) {
        this.productionReadinessService = productionReadinessService;
    }

    @GetMapping
    public Map<String, Object> snapshot() {
        return productionReadinessService.buildSnapshot();
    }
}
