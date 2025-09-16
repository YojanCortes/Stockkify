package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoImagenController {

    private final ProductoRepository productoRepository;

    @Value("${app.uploads.productos-dir:uploads/productos}")
    private String productosDir;

    @GetMapping("/{codigoBarras}/imagen")
    public ResponseEntity<byte[]> obtenerImagen(@PathVariable String codigoBarras) {
        log.info("GET imagen - código barras: {}", codigoBarras);

        // 1) DISCO
        ResponseFromDisk fromDisk = loadFromDisk(codigoBarras);
        if (fromDisk != null) {
            log.info("GET imagen - Servida desde DISCO: {}", fromDisk.path().toAbsolutePath());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(fromDisk.contentType()))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600, public")
                    .body(fromDisk.bytes());
        } else {
            log.info("GET imagen - No encontrada en DISCO para {}", codigoBarras);
        }

        // 2) BD (BLOB)
        Optional<Producto> opt = productoRepository.findByCodigoBarras(codigoBarras);
        if (opt.isPresent()) {
            Producto p = opt.get();
            if (p.getImagen() != null && p.getImagen().length > 0) {
                String ctype = (p.getImagenContentType() != null && !p.getImagenContentType().isBlank())
                        ? p.getImagenContentType() : MediaType.IMAGE_JPEG_VALUE;
                log.info("GET imagen - Servida desde BD (BLOB) para {}", codigoBarras);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(ctype))
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=3600, public")
                        .body(p.getImagen());
            } else {
                log.info("GET imagen - En BD sin BLOB para {}", codigoBarras);
            }
        } else {
            log.warn("GET imagen - Producto no existe: {}", codigoBarras);
        }

        // 3) Placeholder
        try {
            ClassPathResource cpr = new ClassPathResource("static/img/no-image.png");
            byte[] bytes = cpr.getContentAsByteArray();
            log.info("GET imagen - Servida placeholder para {}", codigoBarras);
            return ResponseEntity.status(HttpStatus.OK)
                    .contentType(MediaType.IMAGE_PNG)
                    .body(bytes);
        } catch (IOException e) {
            log.error("GET imagen - Error cargando placeholder: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private ResponseFromDisk loadFromDisk(String codigoBarras) {
        String[] exts = {".jpg", ".jpeg", ".png", ".webp"};
        for (String ext : exts) {
            Path path = Path.of(productosDir).resolve(codigoBarras + ext);
            if (Files.exists(path)) {
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    String ctype = probeOrDefault(path);
                    return new ResponseFromDisk(path, bytes, ctype);
                } catch (IOException e) {
                    log.warn("loadFromDisk - No se pudo leer {}: {}", path.toAbsolutePath(), e.getMessage());
                }
            }
        }
        return null;
    }

    private String probeOrDefault(Path p) {
        try {
            String ctype = Files.probeContentType(p);
            if (ctype != null) return ctype;
        } catch (IOException ignored) { }
        String name = p.getFileName().toString().toLowerCase();
        if (name.endsWith(".png"))  return MediaType.IMAGE_PNG_VALUE;
        if (name.endsWith(".webp")) return "image/webp";
        return MediaType.IMAGE_JPEG_VALUE; // default
    }

    private record ResponseFromDisk(Path path, byte[] bytes, String contentType) {}
}
