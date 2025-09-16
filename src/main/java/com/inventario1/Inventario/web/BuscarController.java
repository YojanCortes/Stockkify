package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class BuscarController {

    private final ProductoRepository productoRepository;

    // =========================
    // NUEVA PÁGINA: /buscar
    // =========================
    @GetMapping("/buscar")
    public String buscarNueva(@RequestParam(value = "q", required = false) String q,
                              @PageableDefault(size = 5, sort = "codigoBarras") Pageable pageable,
                              Model model) {
        Page<Producto> page;

        if (q == null || q.isBlank()) {
            page = productoRepository.findAll(pageable);
        } else {
            String query = q.trim();
            page = productoRepository.findByNombreContainingIgnoreCaseOrCodigoBarrasContaining(
                    query, query, pageable);
        }

        model.addAttribute("q", q);
        model.addAttribute("page", page);
        return "buscar";
    }

    // ===========================================
    // PÁGINA ANTIGUA (lista simple)
    // ===========================================
    @GetMapping("/buscar-lista")
    public String buscarAntigua(@RequestParam(value = "q", required = false) String q,
                                Model model) {
        List<Producto> productos;

        if (q == null || q.isBlank()) {
            // Reemplazo de findTop200ByOrderByCodigoBarrasAsc() usando PageRequest
            productos = productoRepository
                    .findAll(PageRequest.of(0, 200, Sort.by("codigoBarras").ascending()))
                    .getContent();
        } else {
            String query = q.trim();
            if (query.matches("\\d{8,14}")) { // parece código de barras → busca exacto
                Optional<Producto> p = productoRepository.findByCodigoBarras(query);
                productos = p.map(List::of).orElseGet(() -> productoRepository.search(query));
            } else {
                // búsqueda parcial por nombre o código
                productos = productoRepository.search(query);
            }
        }

        model.addAttribute("q", q);
        model.addAttribute("productos", productos);
        return "buscar_antiguo";
    }
}
