package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.services.InventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final InventarioService inventarioService;

    @GetMapping({"/", "/dashboard", "/index"})
    public String verDashboard(Model model) {
        // Top 3 para la ventanita emergente
        var top3 = inventarioService.obtenerAlertasTop(3);

        model.addAttribute("alertasTop3", top3);                 // 👈 clave que usa index.html
        model.addAttribute("totalAlertas", inventarioService.contarAlertas());

        // Aquí puedes añadir KPIs adicionales si quieres
        return "index";
    }
}
