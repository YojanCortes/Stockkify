// src/main/java/com/inventario1/Inventario/controllers/UrlController.java
package com.inventario1.Inventario.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UrlController {

    @GetMapping("/")
    public String home() {
        return "index"; // templates/index.html
    }

    @GetMapping("/register")
    public String showRegister() {
        return "empleadosform"; // templates/empleadosform.html
    }

    // ⛔️ NO definir aquí @GetMapping("/empleados")
    // Si quieres desde aquí ir a empleados, usa:
    // @GetMapping("/empleadosredir")
    // public String goEmpleados() { return "redirect:/empleados"; }
}
