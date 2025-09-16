package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller // IMPORTANTE: @Controller (NO @RestController)
@RequiredArgsConstructor
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;

    // Detalle por código de barras
    @GetMapping("/{codigoBarras}")
    public String verPorCodigo(@PathVariable String codigoBarras, Model model) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("producto", p);
        return "detalles_productos";
    }

    // Formulario de edición
    @GetMapping("/{codigoBarras}/editar")
    public String editarForm(@PathVariable String codigoBarras, Model model) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("producto", p);
        // Si tu plantilla usa th:object="${form}", descomenta la siguiente línea:
        // model.addAttribute("form", p);
        return "editar_producto";
    }

    // Guardar cambios (incluida la imagen SUBIDA desde el formulario)
    @PostMapping("/{codigoBarras}/editar")
    public String actualizar(@PathVariable String codigoBarras,
                             @ModelAttribute("producto") Producto form,
                             // OJO: debe coincidir con name="archivoImagen" en el input file del HTML
                             @RequestParam(value = "archivoImagen", required = false) MultipartFile archivoImagen,
                             RedirectAttributes ra) {

        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Copiar campos editables desde el formulario
        p.setNombre(form.getNombre());
        p.setMarca(form.getMarca());
        p.setCategoria(form.getCategoria());
        p.setUnidadBase(form.getUnidadBase());
        p.setGraduacionAlcoholica(form.getGraduacionAlcoholica());
        p.setVolumenNominalMl(form.getVolumenNominalMl());
        p.setPerecible(form.getPerecible());
        p.setRetornable(form.getRetornable());
        p.setStockActual(form.getStockActual());
        p.setStockMinimo(form.getStockMinimo());
        p.setFechaVencimiento(form.getFechaVencimiento());
        p.setActivo(form.getActivo());

        // Procesar imagen (opcional). NO choca con los endpoints; solo guarda bytes/metadatos.
        if (archivoImagen != null && !archivoImagen.isEmpty()) {
            try {
                p.setImagen(archivoImagen.getBytes());
                p.setImagenContentType(archivoImagen.getContentType());
                p.setImagenNombre(archivoImagen.getOriginalFilename());
                p.setImagenTamano(archivoImagen.getSize());
                // Si además guardas una URL pública, setéala aquí:
                // p.setImagenUrl("/productos/" + codigoBarras + "/imagen");
            } catch (IOException e) {
                ra.addFlashAttribute("error", "No se pudo procesar la imagen: " + e.getMessage());
                return "redirect:/productos/" + codigoBarras + "/editar";
            }
        }

        productoRepository.save(p);
        ra.addFlashAttribute("ok", "Producto actualizado correctamente");
        return "redirect:/productos/" + codigoBarras;
    }

    // ⚠️ Eliminados:
    // @GetMapping("/{codigoBarras}/imagen")
    // @GetMapping("/id/{id}/imagen")
    // y el método privado buildImagenResponse(...)
}
