package com.socialhub.production.web;

import com.socialhub.production.ProductionService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class ProductionController {

    private final ProductionService production;

    ProductionController(ProductionService production) {
        this.production = production;
    }

    /** Ejecuta una acción sobre una tarea. Solo se aceptan las permitidas en su estado actual (si no, 409). */
    @PostMapping("/tasks/{id}/actions/{action}")
    ResponseEntity<Void> taskAction(@PathVariable String id, @PathVariable String action,
                                    @RequestBody(required = false) Map<String, String> body) {
        production.perform(id, action, body == null ? null : body.get("model"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stations/{id}/actions/pause")
    ResponseEntity<Void> pause(@PathVariable String id) {
        production.pauseStation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stations/{id}/actions/resume")
    ResponseEntity<Void> resume(@PathVariable String id) {
        production.resumeStation(id);
        return ResponseEntity.noContent().build();
    }
}
