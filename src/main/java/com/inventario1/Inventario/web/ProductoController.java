package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.TipoMovimiento;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;

    // 🔌 Servicio de movimientos (opcional). Si no está definido, el stock igual se actualiza.
    @Autowired(required = false)
    private MovimientosService movimientosService;

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

    // ====== ACTUALIZAR STOCK (suma cantidad al stock actual y registra movimiento ENTRADA) ======
    @PostMapping("/actualizar-stock")
    public String actualizarStock(@RequestParam("codigoBarras") String codigoBarras,
                                  @RequestParam("cantidad") Integer cantidad,
                                  @RequestParam(value = "idemp", required = false) String idemp, // ← idempotency-key opcional
                                  RedirectAttributes ra) {
        String cb = codigoBarras == null ? "" : codigoBarras.trim();
        if (cb.isEmpty()) {
            ra.addFlashAttribute("error", "Debe ingresar un código de barras.");
            return "redirect:/?error=" + UriUtils.encode("Debe ingresar un código de barras.", StandardCharsets.UTF_8);
        }
        if (cantidad == null || cantidad <= 0) {
            ra.addFlashAttribute("error", "La cantidad debe ser un número mayor a 0.");
            return "redirect:/productos/" + cb;
        }

        Producto p = productoRepository.findByCodigoBarras(cb).orElse(null);
        if (p == null) {
            // No existe → redirige a flujo de creación (no intentamos crear aquí)
            ra.addFlashAttribute("error", "No existe un producto con el código: " + cb);
            return "redirect:/productos/buscar?q=" + UriUtils.encode(cb, StandardCharsets.UTF_8);
        }

        int antes = p.getStockActual() == null ? 0 : p.getStockActual();
        int despues = antes + cantidad;
        p.setStockActual(despues);
        productoRepository.save(p);

        // Referencia idempotente (para evitar duplicar movimiento en reintentos)
        String referencia = (idemp != null && !idemp.isBlank())
                ? "UI:" + idemp
                : "UI:" + cb + ":" + System.currentTimeMillis();

        // Registrar movimiento (si hay servicio disponible)
        try {
            if (movimientosService != null) {
                movimientosService.registrarMovimiento(
                        p,
                        TipoMovimiento.ENTRADA,
                        cantidad,
                        "Actualización rápida de stock desde formulario",
                        referencia
                );
            }
        } catch (Exception ex) {
            log.warn("No se pudo registrar el movimiento de stock (se actualizó igual). Causa: {}", ex.getMessage());
        }

        ra.addFlashAttribute("ok", "Stock actualizado (+" + cantidad + "). Stock actual: " + despues);
        return "redirect:/productos/" + cb;
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

    // ====== API: detalle JSON para autocompletar por CÓDIGO DE BARRAS ======
    @GetMapping(value = "/api/detalle/{codigoBarras}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ProductoMiniDTO> detalleJsonPorCodigo(@PathVariable String codigoBarras) {
        return productoRepository.findByCodigoBarras(codigoBarras.trim())
                .map(p -> ResponseEntity.ok(ProductoMiniDTO.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ====== API: detalle JSON por ID (opcional) ======
    @GetMapping(value = "/api/detalle/id/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ProductoMiniDTO> detalleJsonPorId(@PathVariable Long id) {
        return productoRepository.findById(id)
                .map(p -> ResponseEntity.ok(ProductoMiniDTO.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ====== BUSCAR PRODUCTO (redirige a detalles o vuelve con ?error=...) ======
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

    // ====== DTO ligero para respuestas JSON (sin bytes de imagen) ======
    public static class ProductoMiniDTO {
        public Long id;
        public String codigoBarras;
        public String nombre;
        public String marca;
        public String categoria;     // enum.name()
        public String unidadBase;    // enum.name()
        public Integer volumenNominalMl;
        public Double graduacionAlcoholica;
        public String fechaVencimiento; // yyyy-MM-dd
        public Integer stockActual;
        public Integer stockMinimo;
        public Boolean perecible;
        public Boolean retornable;
        public Boolean activo;

        public static ProductoMiniDTO from(Producto p) {
            ProductoMiniDTO dto = new ProductoMiniDTO();
            dto.id = p.getId();
            dto.codigoBarras = p.getCodigoBarras();
            dto.nombre = p.getNombre();
            dto.marca = p.getMarca();
            dto.categoria = p.getCategoria() != null ? p.getCategoria().name() : null;
            dto.unidadBase = p.getUnidadBase() != null ? p.getUnidadBase().name() : null;
            dto.volumenNominalMl = p.getVolumenNominalMl();
            dto.graduacionAlcoholica = p.getGraduacionAlcoholica();
            dto.fechaVencimiento = p.getFechaVencimiento() != null ? p.getFechaVencimiento().toString() : null;
            dto.stockActual = p.getStockActual();
            dto.stockMinimo = p.getStockMinimo();
            dto.perecible = p.getPerecible();
            dto.retornable = p.getRetornable();
            dto.activo = p.getActivo();
            return dto;
        }
    }

    // ====== Puerto simple para registrar movimientos (impleméntalo en tu capa de servicios) ======
    public interface MovimientosService {
        void registrarMovimiento(Producto producto,
                                 TipoMovimiento tipo,
                                 int cantidad,
                                 String motivo,
                                 String referencia);
    }
}
