package com.mimope.server;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  @GetMapping("/")
  public Map<String, String> index() {
    return Map.of("service", "mimope-server", "status", "ready");
  }
}