package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import com.inventario1.Inventario.web.dto.ProductoCrearForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ProductoCrearController {

    private final ProductoRepository productoRepository;

    /** Directorio donde se guardan las imágenes (configurable) */
    @Value("${app.uploads.productos-dir:uploads/productos}")
    private String productosDir;

    /* =========================================================
       GET: Formulario de creación
       ========================================================= */
    @GetMapping("/productos/agregar")
    public String agregarForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ProductoCrearForm());
        }
        // Ajusta el nombre si tu archivo se llama distinto (p. ej. "agregar_prodcuto")
        return "agregar_producto";
    }

    /* =========================================================
       POST: Crear producto
       ========================================================= */
    @PostMapping("/productos/agregar")
    public String crear(@Valid @ModelAttribute("form") ProductoCrearForm form,
                        BindingResult br,
                        RedirectAttributes ra,
                        Model model) {

        // Unicidad de código de barras
        if (!br.hasErrors() && productoRepository.existsByCodigoBarras(form.getCodigoBarras())) {
            br.rejectValue("codigoBarras", "exists", "Ya existe un producto con ese código de barras.");
        }

        if (br.hasErrors()) {
            // Volver al mismo HTML con mensajes de error
            return "agregar_producto";
        }

        // Mapear DTO -> Entidad
        Producto p = new Producto();
        p.setNombre(form.getNombre() != null ? form.getNombre().trim() : null);
        p.setMarca(form.getMarca());
        p.setCategoria(form.getCategoria());
        p.setUnidadBase(form.getUnidadBase());
        p.setVolumenNominalMl(form.getVolumenNominalMl());
        p.setGraduacionAlcoholica(form.getGraduacionAlcoholica());
        p.setFechaVencimiento(form.getFechaVencimiento());
        p.setStockActual(form.getStockActual() != null ? form.getStockActual() : 0);
        p.setStockMinimo(form.getStockMinimo() != null ? form.getStockMinimo() : 0);
        p.setCodigoBarras(form.getCodigoBarras());
        p.setPerecible(Boolean.TRUE.equals(form.getPerecible()));
        p.setRetornable(Boolean.TRUE.equals(form.getRetornable()));
        p.setActivo(form.getActivo() == null ? true : form.getActivo());

        // Guardar primero (por si necesitas ID u otros defaults)
        p = productoRepository.save(p);

        // Manejar imagen si viene
        if (form.getImagen() != null && !form.getImagen().isEmpty()) {
            try {
                String ext = detectarExtension(form.getImagen());
                if (ext == null) ext = ".png";

                Path baseDir = Path.of(productosDir);
                Files.createDirectories(baseDir);

                String fileName = p.getCodigoBarras() + ext;
                Path destino = baseDir.resolve(fileName);

                Files.copy(form.getImagen().getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

                p.setImagenContentType(form.getImagen().getContentType());
                p.setImagenNombre(form.getImagen().getOriginalFilename());
                p.setImagenTamano(form.getImagen().getSize());
                p.setImagenUrl("/uploads/productos/" + fileName); // asegúrate de servir /uploads/**

                productoRepository.save(p);
            } catch (IOException e) {
                log.warn("No se pudo guardar la imagen del producto {}: {}", p.getCodigoBarras(), e.getMessage());
                ra.addFlashAttribute("warn", "Producto creado, pero la imagen no pudo guardarse.");
            }
        }

        ra.addFlashAttribute("ok", "Producto creado correctamente.");
        // Redirige a la vista del producto o al buscador, como prefieras:
        return "redirect:/productos/" + p.getCodigoBarras();
        // return "redirect:/buscar";
    }

    /* ========================= Helpers ========================= */

    private String detectarExtension(org.springframework.web.multipart.MultipartFile file) {
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
