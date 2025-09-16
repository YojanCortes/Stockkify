package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import com.inventario1.Inventario.services.ProductoService;
import com.inventario1.Inventario.services.ProductoService.DeleteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/productos")
@Slf4j
public class ProductoAccionesController {

    private final ProductoRepository productoRepository;
    private final ProductoService productoService;

    /** Directorio donde se guardan las imágenes (puedes sobreescribir en application.properties) */
    @Value("${app.uploads.productos-dir:uploads/productos}")
    private String productosDir;

    /* =========================================================
       ELIMINAR (AJAX) -> devuelve JSON al modal
       ========================================================= */
    @PostMapping(
            value = "/{codigoBarras}/eliminar",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public ResponseEntity<Map<String, Object>> eliminarAjax(@PathVariable String codigoBarras) {
        log.info("AJAX eliminar solicitado para: {}", codigoBarras);

        Map<String, Object> body = new HashMap<>();
        body.put("redirect", "/buscar");

        DeleteResult result = productoService.eliminarOInactivarPorCodigo(codigoBarras);

        switch (result) {
            case BORRADO -> {
                body.put("ok", true);
                body.put("status", 200);
                body.put("message", "Producto eliminado correctamente.");
                return ResponseEntity.ok(body);
            }
            case INACTIVADO -> {
                body.put("ok", false);
                body.put("status", 409);
                body.put("message", "No se puede eliminar porque tiene movimientos asociados. El producto fue marcado como INACTIVO.");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }
            default -> { // NO_EXISTE
                body.put("ok", false);
                body.put("status", 404);
                body.put("message", "Producto no encontrado.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
        }
    }

    /* =========================================================
       ELIMINAR (NO-AJAX) -> fallback; redirige a /buscar
       ========================================================= */
    @PostMapping(value = "/{codigoBarras}/eliminar", produces = MediaType.TEXT_HTML_VALUE)
    public String eliminar(@PathVariable String codigoBarras, RedirectAttributes ra) {
        log.info("Eliminar (no-AJAX) solicitado para: {}", codigoBarras);

        DeleteResult result = productoService.eliminarOInactivarPorCodigo(codigoBarras);

        switch (result) {
            case BORRADO -> ra.addFlashAttribute("ok", "Producto eliminado correctamente.");
            case INACTIVADO -> ra.addFlashAttribute("warn", "No se puede eliminar porque tiene movimientos asociados. El producto fue marcado como INACTIVO.");
            case NO_EXISTE -> ra.addFlashAttribute("error", "Producto no encontrado.");
        }

        return "redirect:/buscar";
    }

    /* =========================================================
       IMAGEN (redirige a /buscar)
       Acepta param "imagen" (coincide con tu form th:field="*{imagen}") o "file".
       ========================================================= */
    @PostMapping("/{codigoBarras}/imagen")
    @Transactional
    public String actualizarImagen(@PathVariable String codigoBarras,
                                   @RequestParam(value = "imagen", required = false) MultipartFile imagenParam,
                                   @RequestParam(value = "file", required = false) MultipartFile fileParam,
                                   RedirectAttributes ra) {
        MultipartFile file = resolveFile(imagenParam, fileParam);

        log.info("Actualizar imagen para {} - nombreOriginal={}, contentType={}, size={}",
                codigoBarras,
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getContentType() : null,
                file != null ? file.getSize() : null);

        Producto prod = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("warn", "No se adjuntó ningún archivo.");
            return "redirect:/buscar";
        }

        try {
            // Crear directorio si no existe
            Path baseDir = Path.of(productosDir);
            Files.createDirectories(baseDir);

            String ext = detectExtension(file);
            if (ext == null) ext = ".png";

            String fileName = codigoBarras + ext;
            Path destino = baseDir.resolve(fileName);

            log.info("Guardando imagen en {}", destino.toAbsolutePath());
            Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            // Metadatos en la entidad (ajusta a tus campos reales)
            prod.setImagenContentType(file.getContentType());
            prod.setImagenNombre(file.getOriginalFilename());
            prod.setImagenTamano(file.getSize());
            prod.setImagenUrl("/uploads/productos/" + fileName); // asegúrate de servir /uploads/**

            productoRepository.save(prod);

            ra.addFlashAttribute("ok", "Imagen actualizada correctamente.");
        } catch (IOException e) {
            log.error("Error guardando imagen de {}: {}", codigoBarras, e.getMessage(), e);
            ra.addFlashAttribute("error", "No se pudo guardar la imagen.");
        }

        return "redirect:/buscar";
    }

    /* ----------------------- Helpers ----------------------- */

    private MultipartFile resolveFile(MultipartFile imagen, MultipartFile file) {
        if (imagen != null && !imagen.isEmpty()) return imagen;
        if (file != null && !file.isEmpty()) return file;
        return null;
    }

    private String detectExtension(MultipartFile file) {
        String ctype = file.getContentType();
        if (ctype != null) {
            if ("image/png".equalsIgnoreCase(ctype))  return ".png";
            if ("image/jpeg".equalsIgnoreCase(ctype)) return ".jpg";
            if ("image/webp".equalsIgnoreCase(ctype)) return ".webp";
        }
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            String ext = original.substring(original.lastIndexOf('.')).toLowerCase();
            if (ext.matches("\\.(png|jpg|jpeg|webp)")) return ext.equals(".jpeg") ? ".jpg" : ext;
        }
        return null;
    }
}
