// path: src/main/java/com/inventario1/Inventario/controllers/BulkProductoController.java
package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.services.BulkMovimientoLoaderService;
import com.inventario1.Inventario.services.BulkProductoLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping({"/bulk-productos", "/ui/bulk-productos"})
@RequiredArgsConstructor
@Slf4j
public class BulkProductoController {

    private final BulkProductoLoaderService productoService;
    private final BulkMovimientoLoaderService movimientoService;

    @GetMapping
    String page() {
        return "carga_productos";
    }

    @PostMapping("/upload")
    String upload(@RequestParam("file") MultipartFile file,
                  @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
                  Model model) {
        try {
            if (file == null || file.isEmpty()) {
                model.addAttribute("ok", "false");
                model.addAttribute("errorMsg", "Archivo vacío o no enviado.");
                return "carga_productos";
            }

            // Leer bytes una vez (sirve para ambos servicios)
            byte[] bytes = file.getBytes();
            String header = firstLineUtf8(bytes);

            // Heurística: si parece MOVIMIENTOS => delega automáticamente
            if (isMovimientosHeader(header)) {
                var res = movimientoService.importar(
                        new InMemoryMultipartFile("file", file.getOriginalFilename(), file.getContentType(), bytes),
                        dryRun
                );

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
                if (res.getWarnings()!=null && !res.getWarnings().isEmpty())
                    model.addAttribute("warnings", res.getWarnings());
                if (res.getTables()!=null)
                    model.addAttribute("tables", res.getTables());
                model.addAttribute("fmt","Movimientos");
                return "carga_movimientos";
            }

            // Flujo normal de PRODUCTOS
            var res = productoService.importar(
                    new InMemoryMultipartFile("file", file.getOriginalFilename(), file.getContentType(), bytes),
                    dryRun
            );

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
            if (res.getWarnings()!=null && !res.getWarnings().isEmpty())
                model.addAttribute("warnings", res.getWarnings());
            if (res.getTables()!=null)
                model.addAttribute("tables", res.getTables());
            model.addAttribute("fmt","Productos");
            return "carga_productos";

        } catch (Exception e) {
            log.error("[BULK-PRODUCTOS] Error", e);
            model.addAttribute("ok","false");
            model.addAttribute("errorMsg","No se pudo procesar: " + e.getMessage());
            return "carga_productos";
        }
    }

    // --- helpers ---
    private static String firstLineUtf8(byte[] bytes) {
        String s = new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.UTF_8);
        int nl = s.indexOf('\n');
        return (nl >= 0) ? s.substring(0, nl) : s;
    }

    private static boolean isMovimientosHeader(String header) {
        if (header == null) return false;
        String h = header.toLowerCase();
        boolean hasCodigo = h.contains("codigo_barra") || h.contains("codigo_barras");
        boolean hasFecha  = h.contains("fecha");
        boolean hasTipo   = h.contains("tipo");
        boolean hasCant   = h.contains("cantidad") || h.contains("qty") || h.contains("quantity");
        return hasCodigo && hasFecha && hasTipo && hasCant;
    }

    /** MultipartFile en memoria para reusar el mismo upload en ambos servicios. */
    static final class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content != null ? content : new byte[0];
        }
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public ByteArrayInputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) { throw new UnsupportedOperationException("transferTo no soportado"); }
    }
}
