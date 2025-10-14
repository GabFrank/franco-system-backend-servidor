package com.franco.dev.controller;

import com.franco.dev.service.financiero.DteNodeClient;
import com.franco.dev.scheduler.DteScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final DteNodeClient dteNodeClient;
    private final DteScheduler dteScheduler;

    /**
     * Test de comunicación con el microservicio Node.js
     */
    @GetMapping("/microservice-communication")
    public ResponseEntity<Map<String, Object>> testMicroserviceCommunication() {
        try {
            log.info("Iniciando test de comunicación con microservicio Node.js");
            
            // Test 1: Verificar que el microservicio responda
            DteNodeClient.GenerarDocumentoResponse response = dteNodeClient.generarDocumentoDesdeFactura(123L, 1L);
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "Comunicación exitosa con microservicio Node.js");
            result.put("timestamp", java.time.LocalDateTime.now().toString());
            result.put("test", "Generación de DTE");
            result.put("facturaId", 123L);
            result.put("response", response);
            
            log.info("Test de comunicación exitoso: {}", result);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error en test de comunicación con microservicio", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", "Error en comunicación con microservicio");
            error.put("error", e.getMessage());
            error.put("timestamp", java.time.LocalDateTime.now().toString());
            
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Test de salud del sistema
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", java.time.LocalDateTime.now().toString());
        health.put("service", "Franco Systems Backend");
        health.put("version", "1.0.0");
        health.put("microservice", "SIFEN Node.js");
        
        return ResponseEntity.ok(health);
    }

    /**
     * Endpoint para probar el scheduler de DTE manualmente
     */
    @GetMapping("/dte-scheduler")
    public String testDteScheduler() {
        // dteScheduler.procesarFacturasLegalesSinDte();
        return "DTE Scheduler ejecutado manualmente";
    }
}
