package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
class BuscarController {

    final ProductoRepository productoRepository;

    @GetMapping("/buscar")
    String buscarActivos(@RequestParam(value = "q", required = false) String q,
                         @PageableDefault(size = 5, sort = "codigoBarras") Pageable pageable,
                         Model model) {
        Page<Producto> page = (q == null || q.isBlank())
                ? productoRepository.findByActivoTrue(pageable)
                : productoRepository.search(q.trim(), pageable);

        model.addAttribute("q", q);
        model.addAttribute("page", page);
        return "buscar";
    }

    @GetMapping("/buscar-inactivo")
    String buscarInactivos(@RequestParam(value = "q", required = false) String q,
                           @PageableDefault(size = 5, sort = "codigoBarras") Pageable pageable,
                           Model model) {
        Page<Producto> page = (q == null || q.isBlank())
                ? productoRepository.findByActivoFalse(pageable)
                : productoRepository.searchInactivos(q.trim(), pageable);

        model.addAttribute("q", q);
        model.addAttribute("page", page);
        return "buscarproductoinativo";
    }
}
