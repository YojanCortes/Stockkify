package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.services.BulkMovimientoLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui/bulk-movimientos")
@RequiredArgsConstructor
@Slf4j
public class BulkMovimientoController {

    private final BulkMovimientoLoaderService service;

    @GetMapping
    public String page() {
        // Renderiza templates/carga_movimientos.html
        return "carga_movimientos";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         // 🔴 Por defecto en false (persistirá si no marcas el checkbox)
                         @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
                         RedirectAttributes ra) {
        try {
            log.info("[BULK MOV] Inicio | dryRun={}", dryRun);
            var res = service.importar(file, dryRun);

            ra.addFlashAttribute("ok", res.getSkippedRows() == 0 ? "true" : "partial");
            ra.addFlashAttribute("msg",
                    "Total: " + res.getTotalRows() +
                            " | Insertados: " + res.getPersistedRows() +
                            " | Saltados: " + res.getSkippedRows() +
                            (dryRun ? " (PRUEBA: no se guardó)" : "")
            );
            ra.addFlashAttribute("res", res);
            if (res.getErrors() != null && !res.getErrors().isEmpty()) {
                ra.addFlashAttribute("errores", res.getErrors());
            }
            ra.addFlashAttribute("fmt", "Movimientos");
        } catch (Exception e) {
            log.error("[BULK MOV] Error", e);
            ra.addFlashAttribute("ok", "false");
            ra.addFlashAttribute("errorMsg", "No se pudo procesar: " + e.getMessage());
        }
        return "redirect:/ui/bulk-movimientos";
    }
}
