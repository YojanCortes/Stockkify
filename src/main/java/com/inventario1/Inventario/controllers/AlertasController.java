package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.services.InventarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AlertasController {

    private final InventarioService inventarioService;

    public AlertasController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping("/alertas")
    public String verAlertas(Model model) {
        model.addAttribute("alertas", inventarioService.obtenerAlertas());
        return "alertas"; // busca alertas.html en src/main/resources/templates
    }
}
