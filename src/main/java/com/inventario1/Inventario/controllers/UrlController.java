// src/main/java/com/inventario1/Inventario/controllers/UrlController.java
package com.inventario1.Inventario.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UrlController {

    @GetMapping("/home")           // 🔁 antes era "/"
    public String home() {
        return "redirect:/";       // o retorna otra vista si quieres
    }
}
