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

@Controller
@RequiredArgsConstructor
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;

    // ====== DETALLE por código de barras (solo dígitos; evita conflicto con /buscar) ======
    @GetMapping("/{codigoBarras:\\d+}")
    public String verPorCodigo(@PathVariable String codigoBarras, Model model) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        model.addAttribute("producto", p);
        return "detalles_productos";
    }

    // ====== DETALLE por ID ======
    @GetMapping("/detalles/{id}")
    public String verPorId(@PathVariable Long id, Model model) {
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        model.addAttribute("producto", p);
        return "detalles_productos";
    }

    // ====== IMAGEN del producto ======
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

    // ====== FORM de edición ======
    @GetMapping({"/{codigoBarras}/editar", "/editar/{codigoBarras}"})
    public String editarForm(@PathVariable String codigoBarras, Model model) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        model.addAttribute("producto", p);
        return "editar_producto";
    }

    // ====== GUARDAR CAMBIOS ======
    @PostMapping({"/{codigoBarras}/editar", "/editar/{codigoBarras}"})
    public String actualizar(@PathVariable String codigoBarras,
                             @ModelAttribute("producto") Producto form,
                             @RequestParam(value = "archivoImagen", required = false) MultipartFile archivoImagen,
                             RedirectAttributes ra) {

        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

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

    // ====== ELIMINAR ======
    @PostMapping("/{codigoBarras}/eliminar")
    public String eliminar(@PathVariable String codigoBarras, RedirectAttributes ra) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        productoRepository.delete(p);
        ra.addFlashAttribute("ok", "Producto eliminado correctamente");
        return "redirect:/";
    }

    // ====== API: verificar existencia (para el modal, sin redirigir) ======
    @GetMapping("/api/existe/{codigoBarras}")
    @ResponseBody
    public ResponseEntity<Void> existe(@PathVariable String codigoBarras) {
        String cb = codigoBarras == null ? "" : codigoBarras.trim();
        if (cb.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return productoRepository.existsByCodigoBarras(cb)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    // ====== BUSCAR PRODUCTO (link directo: redirige a detalles o vuelve con ?error=...) ======
    // Nota: el modal normalmente usa la API /api/existe y NO este endpoint.
    @GetMapping(value = "/buscar", params = "q")
    public String buscarProducto(@RequestParam("q") String q) {
        String codigo = q == null ? "" : q.trim();

        if (codigo.isEmpty()) {
            return "redirect:/?error=" + UriUtils.encode("Debe ingresar un código de barras.", StandardCharsets.UTF_8);
        }

        if (productoRepository.existsByCodigoBarras(codigo)) {
            return "redirect:/productos/" + codigo;
        } else {
            return "redirect:/?error=" + UriUtils.encode("Producto no existe con el código: " + codigo, StandardCharsets.UTF_8);
        }
    }

    // (opcional) /productos/buscar sin params -> redirige al home
    @GetMapping("/buscar")
    public String buscarSinParametros() {
        return "redirect:/";
    }

    // ====== BUSCAR LEGACY por nombre (compatibilidad) ======
    @GetMapping(value = "/buscar", params = "nombre")
    public String buscarPorNombreLegacy(@RequestParam("nombre") String nombre) {
        String encoded = UriUtils.encode(nombre, StandardCharsets.UTF_8);
        return "redirect:/menu_productos?nombre=" + encoded;
    }
}
