package com.inventario1.Inventario.web;

// src/main/java/com/tuapp/inventario/web/SalidasController.java

import com.inventario1.Inventario.models.RegistrarSalidaRequest;
import com.inventario1.Inventario.models.RegistrarSalidaResponse;
import com.inventario1.Inventario.services.SalidasService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/salidas")
public class SalidasController {

    private final SalidasService salidasService;

    public SalidasController(SalidasService salidasService) {
        this.salidasService = salidasService;
    }

    @PostMapping("/registrar-lote")
    public ResponseEntity<?> registrarLote(@Valid @RequestBody RegistrarSalidaRequest req) {
        try {
            RegistrarSalidaResponse res = salidasService.registrarSalida(req);
            if (!res.ok) return ResponseEntity.badRequest().body(res);
            return ResponseEntity.ok(res);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(error(e.getMessage())); // conflicto (stock)
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(error("Error al registrar la salida."));
        }
    }

    private static Object error(String msg) {
        return new Object() { public final boolean ok = false; public final String error = msg; };
    }
}
