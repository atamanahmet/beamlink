package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.domain.enums.SettingKey;
import com.atamanahmet.beamlink.agent.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/speed-cap")
    public ResponseEntity<Map<String, Double>> getSpeedCap() {
        double value = settingsService.getDouble(SettingKey.TRANSFER_SPEED_CAP_MBPS, 80.0);
        return ResponseEntity.ok(Map.of("value", value));
    }

    @PutMapping("/speed-cap")
    public ResponseEntity<Map<String, Object>> setSpeedCap(@RequestBody Map<String, Double> body) {
        Double value = body.get("value");

        if (value == null || value < 1 || value > 1000) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Value must be between 1 and 1000 Mbps"));
        }

        settingsService.set(SettingKey.TRANSFER_SPEED_CAP_MBPS, value);
        return ResponseEntity.ok(Map.of("value", value));
    }
}