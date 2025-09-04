package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class BuscarController {

    private final ProductoRepository productoRepository;

    public BuscarController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @GetMapping("/buscar")
    public String buscar(@RequestParam(value = "q", required = false) String q, Model model) {
        List<Producto> productos;

        if (q == null || q.isBlank()) {
            // si no hay búsqueda, devuelve los primeros 200
            productos = productoRepository.findTop200ByOrderByCodigoBarrasAsc();
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
        return "buscar";
    }
}
