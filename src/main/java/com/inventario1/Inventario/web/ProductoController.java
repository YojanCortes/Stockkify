package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Controller // IMPORTANTE: @Controller (NO @RestController)
@RequiredArgsConstructor
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;

    // ====== DETALLE por código de barras ======
    @GetMapping("/{codigoBarras}")
    public String verPorCodigo(@PathVariable String codigoBarras, Model model) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        model.addAttribute("producto", p);
        return "detalles_productos";
    }

    // ====== DETALLE por ID (fallback para alertas que traen id) ======
    @GetMapping("/detalles/{id}")
    public String verPorId(@PathVariable Long id, Model model) {
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        model.addAttribute("producto", p);
        return "detalles_productos";
    }

    // ====== IMAGEN del producto (usada por las tarjetas de alertas) ======
    // ⚠ Si existe otro controller con la MISMA ruta, cámbialo/elimínalo para evitar "Ambiguous mapping".
    @GetMapping("/{codigoBarras}/imagen")
    public ResponseEntity<Resource> imagen(@PathVariable String codigoBarras) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        byte[] bytes = p.getImagen();
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no disponible");
        }

        String contentType = p.getImagenContentType() != null ? p.getImagenContentType() : MediaType.IMAGE_JPEG_VALUE;
        String filename = p.getImagenNombre() != null ? p.getImagenNombre() : (p.getCodigoBarras() + ".img");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(new ByteArrayResource(bytes));
    }

    // ====== FORM de edición (soporta AMBAS rutas para coincidir con tu HTML) ======
    @GetMapping({"/{codigoBarras}/editar", "/editar/{codigoBarras}"})
    public String editarForm(@PathVariable String codigoBarras, Model model) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        model.addAttribute("producto", p);
        // Si tu plantilla usa th:object="${form}", descomenta:
        // model.addAttribute("form", p);
        return "editar_producto";
    }

    // ====== GUARDAR cambios (incluida imagen) ======
    @PostMapping({"/{codigoBarras}/editar", "/editar/{codigoBarras}"})
    public String actualizar(@PathVariable String codigoBarras,
                             @ModelAttribute("producto") Producto form,
                             // name="archivoImagen" en el input file
                             @RequestParam(value = "archivoImagen", required = false) MultipartFile archivoImagen,
                             RedirectAttributes ra) {

        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        // Copiar campos editables
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

        // Imagen opcional
        if (archivoImagen != null && !archivoImagen.isEmpty()) {
            try {
                p.setImagen(archivoImagen.getBytes());
                p.setImagenContentType(archivoImagen.getContentType());
                p.setImagenNombre(archivoImagen.getOriginalFilename());
                p.setImagenTamano(archivoImagen.getSize());
            } catch (IOException e) {
                ra.addFlashAttribute("error", "No se pudo procesar la imagen: " + e.getMessage());
                return "redirect:/productos/" + codigoBarras + "/editar";
            }
        }

        productoRepository.save(p);
        ra.addFlashAttribute("ok", "Producto actualizado correctamente");
        return "redirect:/productos/" + codigoBarras;
    }

    // ====== ELIMINAR (coincide con tu formulario del modal) ======
    @PostMapping("/{codigoBarras}/eliminar")
    public String eliminar(@PathVariable String codigoBarras, RedirectAttributes ra) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        productoRepository.delete(p);
        ra.addFlashAttribute("ok", "Producto eliminado correctamente");
        return "redirect:/"; // o a la lista: return "redirect:/productos";
    }

    // ====== (Opcional) BUSCAR por nombre - fallback sin tocar el repositorio ======
    // Si tu HTML llega a usar /productos/buscar?nombre=..., redirige a una lista/menú que ya tengas.
    @GetMapping("/buscar")
    public String buscarPorNombre(@RequestParam("nombre") String nombre) {
        String encoded = UriUtils.encode(nombre, StandardCharsets.UTF_8);
        return "redirect:/menu_productos?nombre=" + encoded;
    }
}
