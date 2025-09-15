package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Categoria;
import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.UnidadBase;
import com.inventario1.Inventario.repos.ProductoRepository;
import com.inventario1.Inventario.web.dto.ProductoEditarForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Controller
@RequestMapping("/productos/editar")
@RequiredArgsConstructor
public class ProductoEditarController {

    private final ProductoRepository productoRepository;

    /**
     * Directorio donde guardaremos las imágenes subidas (configurable).
     * Puedes sobreescribir con: app.uploads.productos-dir=/ruta/absoluta
     */
    @Value("${app.uploads.productos-dir:uploads/productos}")
    private String productosDir;

    /* ---------- Datos comunes para combos en el formulario ---------- */

    @ModelAttribute("categorias")
    public Categoria[] categorias() {
        return Categoria.values();
    }

    @ModelAttribute("unidadesBase")
    public UnidadBase[] unidadesBase() {
        return UnidadBase.values();
    }

    /* ----------------------------- GET ------------------------------ */

    @GetMapping("/{codigoBarras}")
    public String editarForm(@PathVariable String codigoBarras, Model model) {
        Producto producto = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        ProductoEditarForm form = toForm(producto);
        model.addAttribute("form", form);
        return "editar_producto"; // nombre del template Thymeleaf
    }

    /* ----------------------------- POST ----------------------------- */

    @PostMapping(
            value = "/{codigoBarras}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public String actualizar(@PathVariable String codigoBarras,
                             @Valid @ModelAttribute("form") ProductoEditarForm form,
                             BindingResult binding,
                             RedirectAttributes ra,
                             Model model) {

        Producto existente = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        // Validación del form
        if (binding.hasErrors()) {
            // El @ModelAttribute de combos ya los vuelve a inyectar
            return "editar_producto";
        }

        // Aplicar cambios del form -> entidad existente
        apply(form, existente);

        // Persistir
        productoRepository.save(existente);

        // Guardar imagen (opcional)
        try {
            storeImageIfPresent(existente.getCodigoBarras(), form.getImagen());
        } catch (IOException e) {
            // No interrumpimos el flujo; solo avisamos
            ra.addFlashAttribute("error", "El producto se actualizó, pero hubo un problema al guardar la imagen.");
            return "redirect:/productos/" + existente.getCodigoBarras();
        }

        ra.addFlashAttribute("ok", "Producto actualizado correctamente");
        return "redirect:/productos/" + existente.getCodigoBarras();
    }

    /* --------------------------- Helpers ---------------------------- */

    private ProductoEditarForm toForm(Producto p) {
        ProductoEditarForm f = new ProductoEditarForm();
        f.setCodigoBarras(p.getCodigoBarras());
        f.setNombre(p.getNombre());
        f.setMarca(p.getMarca());
        f.setCategoria(p.getCategoria());
        f.setUnidadBase(p.getUnidadBase());
        f.setVolumenNominalMl(p.getVolumenNominalMl());
        f.setGraduacionAlcoholica(p.getGraduacionAlcoholica());
        f.setPerecible(p.getPerecible());
        f.setRetornable(p.getRetornable());
        f.setStockMinimo(p.getStockMinimo());
        f.setFechaVencimiento(p.getFechaVencimiento());
        f.setActivo(p.getActivo());
        // imagen se deja nula; sólo es para subida
        return f;
    }

    private void apply(ProductoEditarForm f, Producto p) {
        // No permitimos cambiar código de barras aquí:
        // p.setCodigoBarras(p.getCodigoBarras());

        p.setNombre(f.getNombre());
        p.setMarca(f.getMarca());
        p.setCategoria(f.getCategoria());
        p.setUnidadBase(f.getUnidadBase());
        p.setVolumenNominalMl(f.getVolumenNominalMl());
        p.setGraduacionAlcoholica(f.getGraduacionAlcoholica());
        p.setPerecible(Boolean.TRUE.equals(f.getPerecible()));
        p.setRetornable(Boolean.TRUE.equals(f.getRetornable()));
        p.setStockMinimo(f.getStockMinimo());
        p.setFechaVencimiento(f.getFechaVencimiento());
        p.setActivo(Boolean.TRUE.equals(f.getActivo()));
        // OJO: stockActual no se toca desde este formulario.
    }

    private void storeImageIfPresent(String codigoBarras, MultipartFile imagen) throws IOException {
        if (imagen == null || imagen.isEmpty()) return;

        // Crear el directorio si no existe
        Path baseDir = Path.of(productosDir);
        Files.createDirectories(baseDir);

        String ext = detectExtension(imagen);
        if (ext == null) {
            // Si no detectamos, por defecto .png
            ext = ".png";
        }
        Path destino = baseDir.resolve(codigoBarras + ext);

        // Guardar (sobrescribe si existía)
        Files.copy(imagen.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
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
            // Permitimos algunas extensiones comunes
            if (ext.matches("\\.(png|jpg|jpeg|webp)")) return ext.equals(".jpeg") ? ".jpg" : ext;
        }
        return null;
    }
}
