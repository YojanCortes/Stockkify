package com.inventario1.Inventario.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UrlController {

    @GetMapping("/")
    public String home() {
        // Redirige al index.html que está en src/main/resources/static/
        return "redirect:/index.html";
    }
}
