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

    @ModelAttribute("seleccionIds")
    public List<String> seleccionIds() { return new ArrayList<>(); }

    @GetMapping({"", "/"})
    public String root() { return "redirect:/salidas/nueva"; }

    @GetMapping({"/nueva", "/nueva/"})
    public String nueva(@RequestParam(value = "codigo", required = false) String codigo,
                        @RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "3") int size,
                        Model model,
                        @ModelAttribute("seleccionIds") List<String> seleccionIds) {

        // Producto puntual por código (flujo original)
        if (codigo != null && !codigo.isBlank()) {
            String cod = codigo.trim();
            try {
                Producto p = inventarioService.leerProducto(cod);
                model.addAttribute("producto", p);
                model.addAttribute("codigo", p.getCodigoBarras());
            } catch (Exception e) {
                model.addAttribute("error", e.getMessage());
                model.addAttribute("codigo", cod);
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("nombre").ascending());
        Page<Producto> pagina = (q != null && !q.isBlank())
                ? inventarioService.buscarProductos(q.trim(), pageable)   // <-- usa service.search(...)
                : inventarioService.listarProductos(pageable);

        List<ProductoView> productosView = pagina.getContent().stream().map(this::toView).collect(Collectors.toList());

        List<ProductoView> seleccionados = seleccionIds.isEmpty()
                ? Collections.emptyList()
                : seleccionIds.stream().map(id -> {
            try { return inventarioService.leerProducto(id); } catch (Exception e) { return null; }
        }).filter(Objects::nonNull).map(this::toView).collect(Collectors.toList());

        model.addAttribute("q", q);
        model.addAttribute("productos", productosView);
        model.addAttribute("page", pagina.getNumber());
        model.addAttribute("totalPages", pagina.getTotalPages());
        model.addAttribute("totalElements", pagina.getTotalElements());
        model.addAttribute("seleccionIds", seleccionIds);
        model.addAttribute("seleccionados", seleccionados);

        return "salida_nueva";
    }

    @PostMapping("/seleccionar")
    public String seleccionar(@RequestParam("id") String id,
                              @ModelAttribute("seleccionIds") List<String> seleccionIds,
                              @RequestParam(value = "q", required = false) String q) {
        if (!seleccionIds.contains(id) && seleccionIds.size() < 3) {
            seleccionIds.add(id);
        }
        return "redirect:/salidas/nueva" + (q != null && !q.isBlank() ? "?q=" + q : "");
    }

    @PostMapping("/quitar")
    public String quitar(@RequestParam("id") String id,
                         @ModelAttribute("seleccionIds") List<String> seleccionIds,
                         @RequestParam(value = "q", required = false) String q) {
        seleccionIds.remove(id);
        return "redirect:/salidas/nueva" + (q != null && !q.isBlank() ? "?q=" + q : "");
    }

    @PostMapping("/continuar")
    public String continuar(@ModelAttribute("seleccionIds") List<String> seleccionIds, Model model) {
        if (seleccionIds.isEmpty()) return "redirect:/salidas/nueva";
        List<ProductoView> seleccionados = seleccionIds.stream().map(id -> {
            try { return inventarioService.leerProducto(id); } catch (Exception e) { return null; }
        }).filter(Objects::nonNull).map(this::toView).collect(Collectors.toList());
        model.addAttribute("seleccionados", seleccionados);
        return "salida_confirmar";
    }

    // ------------ helpers (ViewModel) ------------
    private ProductoView toView(Producto p) {
        return ProductoView.builder()
                .id(nz(p.getCodigoBarras()))
                .nombre(nz(p.getNombre()))
                .sku(nz(p.getCodigoBarras()))
                .marca(p.getMarca())
                .categoria(p.getCategoria())
                .stockActual(p.getCantidad() == null ? 0 : p.getCantidad())
                .stockMinimo(p.getStockMinimo())
                .build();
    }
    private String nz(String s){ return s == null ? "" : s; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    private static class ProductoView {
        private String id;          // codigo_barras
        private String nombre;
        private String sku;         // codigo_barras
        private String marca;
        private String categoria;
        private Integer stockActual; // cantidad
        private Integer stockMinimo; // stock_minimo
    }
}
