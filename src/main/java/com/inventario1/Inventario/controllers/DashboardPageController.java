package com.inventario1.Inventario.controllers;

// src/main/java/com/inventario1/Inventario/web/DashboardPageController.java

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardPageController {

    // Mapeo varias variantes para que no te vuelva a fallar por una letra:
    @GetMapping({"/dashboard", "/dhasboard-pagina", "/dhasboard_pagina", "/dashboad_pagina"})
    public String ver() {
        // Debe coincidir EXACTO con el nombre del template (sin .html)
        return "dhasboard_pagina";
    }
}
