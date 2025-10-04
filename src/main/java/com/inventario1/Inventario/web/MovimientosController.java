package com.inventario1.Inventario.web;

import com.inventario1.Inventario.repos.MovimientoLineaRepository;
import com.inventario1.Inventario.services.dto.MovimientoItemDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class MovimientosController {

    private final MovimientoLineaRepository movimientoLineaRepository;

    @GetMapping("/movimientos")
    public String listar(@RequestParam(name = "p", defaultValue = "0") int p,
                         @RequestParam(name = "s", defaultValue = "20") int s,
                         Model model) {

        int page = Math.max(0, p);
        int size = Math.min(Math.max(1, s), 100);

        Page<MovimientoItemDTO> data =
                movimientoLineaRepository.pageMovimientos(PageRequest.of(page, size));

        model.addAttribute("page", data);
        model.addAttribute("baseUrl", "/movimientos");
        return "movimientos";
    }
}
