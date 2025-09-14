package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.services.InventarioService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/salidas")
@SessionAttributes("seleccionIds")
public class SalidaController {

    private final InventarioService inventarioService;

    public SalidaController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    /** Lista en sesión de códigos de barras seleccionados. */
    @ModelAttribute("seleccionIds")
    public List<String> seleccionIds() {
        return new ArrayList<>();
    }

    @GetMapping({"", "/"})
    public String root() { return "redirect:/salidas/nueva"; }

    @GetMapping({"/nueva", "/nueva/"})
    public String nueva(@RequestParam(value = "codigo", required = false) String codigo,
                        @RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "12") int size,
                        Model model,
                        @ModelAttribute("seleccionIds") List<String> seleccionIds) {

        if (size <= 0) size = 12;

        // Si viene un código puntual (flujo original): busca por codigoBarras
        if (codigo != null && !codigo.isBlank()) {
            String cod = codigo.trim();
            try {
                Producto p = inventarioService.leerProducto(cod); // busca por código de barras
                model.addAttribute("producto", p);
                model.addAttribute("codigo", p.getCodigoBarras());
            } catch (Exception e) {
                model.addAttribute("error", e.getMessage());
                model.addAttribute("codigo", cod);
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("nombre").ascending());
        Page<Producto> pagina = (q != null && !q.isBlank())
                ? inventarioService.buscarProductos(q.trim(), pageable)   // búsqueda por nombre o código
                : inventarioService.listarProductos(pageable);

        List<ProductoView> productosView = pagina.getContent()
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());

        // Reconstruir selección (ids en sesión son códigos de barras)
        List<ProductoView> seleccionados = seleccionIds.isEmpty()
                ? Collections.emptyList()
                : seleccionIds.stream()
                .map(idCodigoBarras -> {
                    try { return inventarioService.leerProducto(idCodigoBarras); }
                    catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .map(this::toView)
                .collect(Collectors.toList());

        model.addAttribute("q", q);
        model.addAttribute("productos", productosView);
        model.addAttribute("page", pagina.getNumber());
        model.addAttribute("totalPages", pagina.getTotalPages());
        model.addAttribute("totalElements", pagina.getTotalElements());
        model.addAttribute("seleccionIds", seleccionIds);
        model.addAttribute("seleccionados", seleccionados);

        return "salida_nueva";
    }

    /** Agrega un producto (por código de barras) a la selección en sesión. */
    @PostMapping("/seleccionar")
    public String seleccionar(@RequestParam("id") String idCodigoBarras,
                              @ModelAttribute("seleccionIds") List<String> seleccionIds,
                              @RequestParam(value = "q", required = false) String q) {
        if (idCodigoBarras != null && !idCodigoBarras.isBlank()
                && !seleccionIds.contains(idCodigoBarras)
                && seleccionIds.size() < 3) {
            seleccionIds.add(idCodigoBarras.trim());
        }
        return "redirect:/salidas/nueva" + buildQ(q);
    }

    /** Quita un producto (por código de barras) de la selección en sesión. */
    @PostMapping("/quitar")
    public String quitar(@RequestParam("id") String idCodigoBarras,
                         @ModelAttribute("seleccionIds") List<String> seleccionIds,
                         @RequestParam(value = "q", required = false) String q) {
        seleccionIds.remove(idCodigoBarras);
        return "redirect:/salidas/nueva" + buildQ(q);
    }

    @PostMapping("/continuar")
    public String continuar(@ModelAttribute("seleccionIds") List<String> seleccionIds, Model model) {
        if (seleccionIds.isEmpty()) return "redirect:/salidas/nueva";
        List<ProductoView> seleccionados = seleccionIds.stream()
                .map(idCodigoBarras -> {
                    try { return inventarioService.leerProducto(idCodigoBarras); }
                    catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .map(this::toView)
                .collect(Collectors.toList());
        model.addAttribute("seleccionados", seleccionados);
        return "salida_confirmar";
    }

    // ------------ helpers (ViewModel) ------------
    private ProductoView toView(Producto p) {
        return ProductoView.builder()
                .id(nz(p.getCodigoBarras()))                    // id lógico en la vista = código de barras
                .nombre(nz(p.getNombre()))
                .sku(nz(p.getCodigoBarras()))
                .marca(p.getMarca())
                .categoria(p.getCategoria() != null ? p.getCategoria().name() : null) // enum -> texto
                .stockActual(p.getStockActual() == null ? 0 : p.getStockActual())
                .stockMinimo(p.getStockMinimo())
                .build();
    }

    private String buildQ(String q) {
        if (q == null || q.isBlank()) return "";
        String enc = URLEncoder.encode(q, StandardCharsets.UTF_8);
        return "?q=" + enc;
    }

    private String nz(String s){ return s == null ? "" : s; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    private static class ProductoView {
        private String id;           // codigo_barras (id lógico para la vista)
        private String nombre;
        private String sku;          // codigo_barras
        private String marca;
        private String categoria;    // texto del enum (GENERAL/ALIMENTOS/INSUMOS)
        private Integer stockActual; // stock_actual
        private Integer stockMinimo; // stock_minimo
    }
}
