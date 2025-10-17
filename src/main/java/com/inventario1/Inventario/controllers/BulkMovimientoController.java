package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.services.BulkMovimientoLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/bulk-movimientos") // <- ruta distinta a /bulk-productos
@RequiredArgsConstructor
@Slf4j
class BulkMovimientoController {

    final BulkMovimientoLoaderService service;

    @GetMapping
    String page() {
        return "carga_movimientos";
    }

    @PostMapping("/upload") // <- quedará POST /bulk-movimientos/upload
    String upload(@RequestParam("file") MultipartFile file,
                  @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
                  Model model) {
        try {
            if (file == null || file.isEmpty()) {
                model.addAttribute("ok", "false");
                model.addAttribute("errorMsg", "Archivo vacío o no enviado.");
                return "carga_movimientos";
            }
            var res = service.importar(file, dryRun);
            var ok = (res.getErrors()!=null && !res.getErrors().isEmpty())
                    ? "partial"
                    : (res.getPersistedRows()>0 ? "true" : "partial");

            model.addAttribute("ok", ok);
            model.addAttribute("msg",
                    "Total: " + res.getTotalRows() +
                            " | Insertados: " + res.getPersistedRows() +
                            " | Saltados: " + res.getSkippedRows() +
                            (dryRun ? " (PRUEBA: no se guardó)" : ""));
            model.addAttribute("res", res);
            if (res.getErrors()!=null && !res.getErrors().isEmpty())
                model.addAttribute("errores", res.getErrors());
            model.addAttribute("fmt","Movimientos");
            return "carga_movimientos";
        } catch (Exception e) {
            log.error("[BULK-MOVIMIENTOS] Error", e);
            model.addAttribute("ok","false");
            model.addAttribute("errorMsg","No se pudo procesar: " + e.getMessage());
            return "carga_movimientos";
        }
    }
}
