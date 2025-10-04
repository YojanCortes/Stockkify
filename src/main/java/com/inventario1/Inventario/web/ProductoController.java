package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.TipoMovimiento;
import com.inventario1.Inventario.repos.ProductoRepository;
import com.inventario1.Inventario.services.MovimientosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final MovimientosService movimientosService; // requiere MovimientosService + MovimientosServiceImpl

    // ====== VISTA: detalle por CÓDIGO DE BARRAS ======
    @GetMapping("/{codigoBarras:\\d+}")
    public String verPorCodigo(@PathVariable String codigoBarras, Model model) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        model.addAttribute("producto", p);
        return "detalles_productos";
    }

    // ====== VISTA: detalle por ID ======
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

    // ====== FORM: crear (GET) ======
    @GetMapping("/agregar")
    public String crearForm(Model model) {
        if (!model.containsAttribute("form")) {
            Producto p = new Producto();
            p.setActivo(true);
            model.addAttribute("form", p);
        }
        return "agregar_producto";
    }

    // ====== GUARDAR: crear/actualizar desde formulario "Agregar producto" (POST) ======
    // ⚠️ Si aún tienes otro controlador con @PostMapping("/agregar"), cámbiale la ruta a ese otro para evitar conflicto.
    @PostMapping("/agregar")
    public String crearOActualizarDesdeForm(@ModelAttribute("form") Producto form,
                                            @RequestParam(value = "cantidad", required = false) Integer cantidad,
                                            @RequestParam(value = "archivoImagen", required = false) MultipartFile archivoImagen,
                                            @RequestParam(value = "imagen", required = false) MultipartFile imagenAlt,
                                            RedirectAttributes ra) {

        String cb = form.getCodigoBarras() == null ? "" : form.getCodigoBarras().trim();
        if (cb.isEmpty()) {
            ra.addFlashAttribute("error", "Debe ingresar un código de barras.");
            return "redirect:/productos/agregar";
        }

        int cant = (cantidad == null ? 0 : Math.max(Integer.MIN_VALUE, cantidad)); // permitimos negativos por seguridad futura

        Producto existente = productoRepository.findByCodigoBarras(cb).orElse(null);

        MultipartFile archivo = (archivoImagen != null && !archivoImagen.isEmpty())
                ? archivoImagen
                : (imagenAlt != null && !imagenAlt.isEmpty() ? imagenAlt : null);

        if (existente != null) {
            // ====== Actualiza datos del existente ======
            existente.setNombre(form.getNombre());
            existente.setMarca(form.getMarca());
            existente.setCategoria(form.getCategoria());
            existente.setUnidadBase(form.getUnidadBase());
            existente.setGraduacionAlcoholica(form.getGraduacionAlcoholica());
            existente.setVolumenNominalMl(form.getVolumenNominalMl());
            existente.setPerecible(form.getPerecible());
            existente.setRetornable(form.getRetornable());
            existente.setStockMinimo(form.getStockMinimo());
            existente.setFechaVencimiento(form.getFechaVencimiento());
            existente.setActivo(form.getActivo() != null ? form.getActivo() : Boolean.TRUE);

            if (archivo != null) {
                try {
                    existente.setImagen(archivo.getBytes());
                    existente.setImagenContentType(archivo.getContentType());
                    existente.setImagenNombre(archivo.getOriginalFilename());
                    existente.setImagenTamano(archivo.getSize());
                } catch (IOException e) {
                    ra.addFlashAttribute("error", "No se pudo procesar la imagen: " + e.getMessage());
                    return "redirect:/productos/agregar";
                }
            }

            int antes = existente.getStockActual() == null ? 0 : existente.getStockActual();
            int despues = antes + Math.max(0, cant); // aquí solo incrementamos con el flujo "agregar"
            existente.setStockActual(despues);
            productoRepository.save(existente);

            if (cant > 0) {
                try {
                    movimientosService.registrarMovimiento(
                            existente,
                            TipoMovimiento.ENTRADA,
                            cant,
                            "Ingreso desde 'Agregar producto' (producto existente)",
                            "UI/AGREGAR_EXISTENTE:" + cb
                    );
                } catch (Exception ex) {
                    log.warn("Stock ok, pero no se pudo registrar movimiento. Causa: {}", ex.getMessage());
                }
            }

            ra.addFlashAttribute("ok",
                    cant > 0
                            ? ("Producto actualizado y stock incrementado (+" + cant + "). Stock actual: " + despues)
                            : "Producto actualizado (sin cambios de stock).");
            return "redirect:/productos/" + cb;
        }

        // ====== Crear nuevo ======
        Producto nuevo = new Producto();
        nuevo.setCodigoBarras(cb);
        nuevo.setNombre(form.getNombre());
        nuevo.setMarca(form.getMarca());
        nuevo.setCategoria(form.getCategoria());
        nuevo.setUnidadBase(form.getUnidadBase());
        nuevo.setGraduacionAlcoholica(form.getGraduacionAlcoholica());
        nuevo.setVolumenNominalMl(form.getVolumenNominalMl());
        nuevo.setPerecible(form.getPerecible());
        nuevo.setRetornable(form.getRetornable());
        nuevo.setStockMinimo(form.getStockMinimo());
        nuevo.setFechaVencimiento(form.getFechaVencimiento());
        nuevo.setActivo(form.getActivo() != null ? form.getActivo() : Boolean.TRUE);

        if (archivo != null) {
            try {
                nuevo.setImagen(archivo.getBytes());
                nuevo.setImagenContentType(archivo.getContentType());
                nuevo.setImagenNombre(archivo.getOriginalFilename());
                nuevo.setImagenTamano(archivo.getSize());
            } catch (IOException e) {
                ra.addFlashAttribute("error", "No se pudo procesar la imagen: " + e.getMessage());
                return "redirect:/productos/agregar";
            }
        }

        int stockInicial = Math.max(0, cant); // en alta solo permitimos entrada
        nuevo.setStockActual(stockInicial);
        productoRepository.save(nuevo);

        if (stockInicial > 0) {
            try {
                movimientosService.registrarMovimiento(
                        nuevo,
                        TipoMovimiento.ENTRADA,
                        stockInicial,
                        "Alta de producto con stock inicial",
                        "UI/AGREGAR_NUEVO:" + cb
                );
            } catch (Exception ex) {
                log.warn("Producto creado, pero no se pudo registrar movimiento inicial. Causa: {}", ex.getMessage());
            }
        }

        ra.addFlashAttribute("ok",
                stockInicial > 0
                        ? ("Producto creado con stock inicial +" + stockInicial)
                        : "Producto creado.");
        return "redirect:/productos/" + cb;
    }

    // ====== FORM de edición (GET) ======
    @GetMapping({"/{codigoBarras}/editar", "/editar/{codigoBarras}"})
    public String editarForm(@PathVariable String codigoBarras, Model model) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        model.addAttribute("producto", p);
        return "editar_producto";
    }

    // ====== GUARDAR cambios de edición (POST) ======
    @PostMapping({"/{codigoBarras}/editar", "/editar/{codigoBarras}"})
    public String actualizar(@PathVariable String codigoBarras,
                             @ModelAttribute("producto") Producto form,
                             @RequestParam(value = "archivoImagen", required = false) MultipartFile archivoImagen,
                             @RequestParam(value = "imagen", required = false) MultipartFile imagenAlt,
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

        MultipartFile archivo = (archivoImagen != null && !archivoImagen.isEmpty())
                ? archivoImagen
                : (imagenAlt != null && !imagenAlt.isEmpty() ? imagenAlt : null);

        if (archivo != null) {
            try {
                p.setImagen(archivo.getBytes());
                p.setImagenContentType(archivo.getContentType());
                p.setImagenNombre(archivo.getOriginalFilename());
                p.setImagenTamano(archivo.getSize());
            } catch (IOException e) {
                ra.addFlashAttribute("error", "No se pudo procesar la imagen: " + e.getMessage());
                return "redirect:/productos/" + codigoBarras + "/editar";
            }
        }

        productoRepository.save(p);
        ra.addFlashAttribute("ok", "Producto actualizado correctamente");
        return "redirect:/productos/" + codigoBarras;
    }

    // ====== ACTUALIZAR STOCK rápido (solo suma) ======
    @PostMapping("/actualizar-stock")
    public String actualizarStock(@RequestParam("codigoBarras") String codigoBarras,
                                  @RequestParam("cantidad") Integer cantidad,
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
            ra.addFlashAttribute("error", "No existe un producto con el código: " + cb);
            return "redirect:/productos/buscar?q=" + UriUtils.encode(cb, StandardCharsets.UTF_8);
        }

        int antes = p.getStockActual() == null ? 0 : p.getStockActual();
        int despues = antes + cantidad;
        p.setStockActual(despues);
        productoRepository.save(p);

        try {
            movimientosService.registrarMovimiento(
                    p,
                    TipoMovimiento.ENTRADA,
                    cantidad,
                    "Actualización rápida de stock desde formulario",
                    "UI/STOCK_RAPIDO:" + cb
            );
        } catch (Exception ex) {
            log.warn("No se pudo registrar el movimiento de stock (se actualizó igual). Causa: {}", ex.getMessage());
        }

        ra.addFlashAttribute("ok", "Stock actualizado (+" + cantidad + "). Stock actual: " + despues);
        return "redirect:/productos/" + cb;
    }

    // ====== AJUSTAR STOCK (suma o resta) ======
    @PostMapping("/ajustar-stock")
    public String ajustarStock(@RequestParam("codigoBarras") String codigoBarras,
                               @RequestParam("cantidad") Integer cantidad,
                               @RequestParam(value = "motivo", required = false) String motivo,
                               RedirectAttributes ra) {

        String cb = codigoBarras == null ? "" : codigoBarras.trim();
        if (cb.isEmpty()) {
            ra.addFlashAttribute("error", "Debe ingresar un código de barras.");
            return "redirect:/?error=" + UriUtils.encode("Debe ingresar un código de barras.", StandardCharsets.UTF_8);
        }
        if (cantidad == null || cantidad == 0) {
            ra.addFlashAttribute("error", "La cantidad debe ser distinta de 0.");
            return "redirect:/productos/" + cb;
        }

        Producto p = productoRepository.findByCodigoBarras(cb).orElse(null);
        if (p == null) {
            ra.addFlashAttribute("error", "No existe un producto con el código: " + cb);
            return "redirect:/productos/buscar?q=" + UriUtils.encode(cb, StandardCharsets.UTF_8);
        }

        int stockActual = p.getStockActual() == null ? 0 : p.getStockActual();

        if (cantidad < 0 && stockActual + cantidad < 0) {
            ra.addFlashAttribute("error", "No puede dejar el stock en negativo.");
            return "redirect:/productos/" + cb;
        }

        int nuevoStock = stockActual + cantidad;
        p.setStockActual(nuevoStock);
        productoRepository.save(p);

        TipoMovimiento tipo = (cantidad > 0) ? TipoMovimiento.ENTRADA : TipoMovimiento.SALIDA;
        int unidades = Math.abs(cantidad);

        try {
            movimientosService.registrarMovimiento(
                    p,
                    tipo,
                    unidades,
                    (motivo == null || motivo.isBlank())
                            ? (tipo == TipoMovimiento.ENTRADA ? "Ajuste de stock (entrada)" : "Ajuste de stock (salida)")
                            : motivo,
                    "UI/AJUSTE_STOCK:" + cb
            );
        } catch (Exception ex) {
            log.warn("No se pudo registrar el movimiento de ajuste. Causa: {}", ex.getMessage());
        }

        String msg = (cantidad > 0)
                ? ("Stock ajustado (+" + unidades + "). Nuevo stock: " + nuevoStock)
                : ("Stock ajustado (-" + unidades + "). Nuevo stock: " + nuevoStock);

        ra.addFlashAttribute("ok", msg);
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

    // ====== API: verificar existencia ======
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

    // ====== API: detalle JSON por CÓDIGO (para autocompletar) ======
    @GetMapping(value = "/api/detalle/{codigoBarras}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ProductoMiniDTO> detalleJsonPorCodigo(@PathVariable String codigoBarras) {
        return productoRepository.findByCodigoBarras(codigoBarras.trim())
                .map(p -> ResponseEntity.ok(ProductoMiniDTO.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ====== API: detalle JSON por ID ======
    @GetMapping(value = "/api/detalle/id/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ProductoMiniDTO> detalleJsonPorId(@PathVariable Long id) {
        return productoRepository.findById(id)
                .map(p -> ResponseEntity.ok(ProductoMiniDTO.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ====== BUSCAR por código (redirige) ======
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

    // /productos/buscar sin params -> home
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

    // ====== DTO ligero para JSON ======
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
}
