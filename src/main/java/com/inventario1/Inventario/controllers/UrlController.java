package com.inventario1.Inventario.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UrlController {

    @GetMapping("/")
    public String home() {
        // Renderiza la plantilla index.html en src/main/resources/templates/
        return "index";
    }

    @GetMapping("/register")
    public String showRegister() {
        // Renderiza la plantilla register.html en src/main/resources/templates/
        return "register";
    }

    // OJO: /salidas lo maneja SalidaController
    // OJO: /buscar lo maneja BuscarController
}
