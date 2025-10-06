package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Categoria;
import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.UnidadBase;
import com.inventario1.Inventario.repos.ProductoRepository;
import com.inventario1.Inventario.web.dto.ProductoEditarForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

@Controller
@RequestMapping("/productos/editar")
@RequiredArgsConstructor
@Slf4j
public class ProductoEditarController {

    private final ProductoRepository productoRepository;

    @Value("${app.uploads.productos-dir:uploads/productos}")
    private String productosDir;

    /* ---------- Datos comunes para combos ---------- */
    @ModelAttribute("categorias")
    public Categoria[] categorias() { return Categoria.values(); }

    @ModelAttribute("unidadesBase")
    public UnidadBase[] unidadesBase() { return UnidadBase.values(); }

    /* ---------- (Opcional) aceptar coma decimal en BigDecimal ---------- */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setDecimalSeparator(',');
        s.setGroupingSeparator('.');
        DecimalFormat df = new DecimalFormat("#,##0.##", s);
        df.setParseBigDecimal(true);
        // Permite 12 enteros y 2 decimales aprox.; el @Valid del DTO valida exactamente.
        binder.registerCustomEditor(BigDecimal.class, new org.springframework.beans.propertyeditors.CustomNumberEditor(BigDecimal.class, df, true));
    }

    /* ----------------------------- POST ----------------------------- */
    @PostMapping(value = "/{codigoBarras}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String actualizar(@PathVariable String codigoBarras,
                             @Valid @ModelAttribute("form") ProductoEditarForm form,
                             BindingResult binding,
                             RedirectAttributes ra) {

        log.info("POST actualizar - código barras: {}", codigoBarras);

        Producto existente = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (binding.hasErrors()) {
            log.warn("POST actualizar - errores de validación: {}", binding.getAllErrors());
            return "editar_producto";
        }

        // Aplicar cambios del form (incluye precio)
        apply(form, existente);

        // Guardar cambios base
        existente = productoRepository.save(existente);
        log.info("POST actualizar - Producto guardado en BD. id={}, version={}",
                existente.getId(), existente.getVersion());

        // Guardar imagen y metadatos (si vino archivo)
        try {
            ImageMeta meta = storeImageIfPresent(existente.getCodigoBarras(), form.getImagen());
            if (meta != null) {
                existente.setImagenUrl("/productos/" + existente.getCodigoBarras() + "/imagen");
                existente.setImagenContentType(meta.contentType());
                existente.setImagenNombre(meta.nombre());
                existente.setImagenTamano(meta.tamano()); // Long
                productoRepository.save(existente);
            }
        } catch (IOException e) {
            log.error("POST actualizar - Error guardando imagen para {}: {}", codigoBarras, e.getMessage(), e);
            ra.addFlashAttribute("error", "El producto se actualizó, pero hubo un problema al guardar la imagen.");
            return "redirect:/productos/" + existente.getCodigoBarras();
        }

        log.info("POST actualizar - Proceso completado OK para {}", codigoBarras);
        ra.addFlashAttribute("ok", "Producto actualizado correctamente");
        return "redirect:/productos/" + existente.getCodigoBarras();
    }

    /* --------------------------- Helpers ---------------------------- */
    private void apply(ProductoEditarForm f, Producto p) {
        p.setNombre(f.getNombre());
        p.setMarca(f.getMarca());
        p.setCategoria(f.getCategoria());
        p.setUnidadBase(f.getUnidadBase());
        p.setVolumenNominalMl(f.getVolumenNominalMl());
        p.setGraduacionAlcoholica(f.getGraduacionAlcoholica());
        p.setPerecible(Boolean.TRUE.equals(f.getPerecible()));
        p.setRetornable(Boolean.TRUE.equals(f.getRetornable()));
        p.setStockActual(f.getStockActual());
        p.setStockMinimo(f.getStockMinimo());
        p.setFechaVencimiento(f.getFechaVencimiento());
        p.setActivo(Boolean.TRUE.equals(f.getActivo()));
        // === NUEVO: precio ===
        p.setPrecio(f.getPrecio()); // requiere get/setPrecio() en la entidad Producto
    }

    /** Guarda la imagen (si viene) y retorna metadatos para persistir en la entidad. */
    private ImageMeta storeImageIfPresent(String codigoBarras, MultipartFile imagen) throws IOException {
        if (imagen == null || imagen.isEmpty()) {
            log.info("storeImageIfPresent - No hay archivo para {}", codigoBarras);
            return null;
        }

        log.info("storeImageIfPresent - Recibido archivo: nombre='{}', contentType={}, size={} bytes",
                imagen.getOriginalFilename(), imagen.getContentType(), imagen.getSize());

        // Crear el directorio si no existe
        Path baseDir = Path.of(productosDir);
        Files.createDirectories(baseDir);

        String ext = detectExtension(imagen);
        if (ext == null) ext = ".png";

        Path destino = baseDir.resolve(codigoBarras + ext);
        log.info("storeImageIfPresent - Guardando imagen en: {}", destino.toAbsolutePath());
        Files.copy(imagen.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        log.info("storeImageIfPresent - Imagen guardada OK ({} bytes)", imagen.getSize());

        String original = imagen.getOriginalFilename();
        String nombre = (original != null && !original.isBlank()) ? original : (codigoBarras + ext);
        String ctype = (imagen.getContentType() != null) ? imagen.getContentType() : MediaType.IMAGE_PNG_VALUE;
        Long tamano = imagen.getSize(); // Long

        return new ImageMeta(nombre, ctype, tamano);
    }

    private String detectExtension(MultipartFile file) {
        String ctype = file.getContentType();
        if (ctype != null) {
            if (ctype.equalsIgnoreCase("image/png")) return ".png";
            if (ctype.equalsIgnoreCase("image/jpeg")) return ".jpg";
            if (ctype.equalsIgnoreCase("image/webp")) return ".webp";
        }
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            String ext = original.substring(original.lastIndexOf('.')).toLowerCase();
            if (ext.matches("\\.(png|jpg|jpeg|webp)")) return ext.equals(".jpeg") ? ".jpg" : ext;
        }
        return null;
    }

    /* ---------- Tipo anidado para metadatos (usa Long) ---------- */
    private record ImageMeta(String nombre, String contentType, Long tamano) {}
}
