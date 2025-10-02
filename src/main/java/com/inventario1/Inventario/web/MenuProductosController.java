package com.inventario1.Inventario.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MenuProductosController {

    // /inventario -> templates/menu_productos.html
    // Sirve el mismo HTML para ambas rutas
    @GetMapping({"/inventario", "/menu_inventario"})
    public String menuProductos() {
        return "menu_inventario"; // src/main/resources/templates/menu_productos.html
    }

    // (Opcional) si quieres que "/" vaya al menú también
    // @GetMapping("/")
    // public String root() {
    //     return "redirect:/inventari o";
    // }
}
