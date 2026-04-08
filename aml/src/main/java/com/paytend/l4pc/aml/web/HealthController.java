package com.paytend.l4pc.aml.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/pc/aml/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "L4PC-AML");
    }
}
