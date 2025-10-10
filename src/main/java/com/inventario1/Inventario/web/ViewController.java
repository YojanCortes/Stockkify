// src/main/java/com/inventario1/Inventario/web/ViewController.java
package com.inventario1.Inventario.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/dashboard/baja-rotacion")
    public String bajaRotacion() { return "dashboard/baja-rotacion"; } // templates/dashboard/baja-rotacion.html

    @GetMapping("/dashboard/rentabilidad")
    public String rentabilidad() { return "dashboard/rentabilidad"; } // templates/dashboard/rentabilidad.html
    // agregar a tu ViewController existente
    @GetMapping("/dashboard/stock-seguridad")
    public String stockSeguridad() { return "dashboard/stock-seguridad"; }
    // agregar a tu ViewController existente
    @GetMapping("/dashboard/stock-obsoleto")
    public String stockObsoleto() { return "dashboard/stock-obsoleto"; }

}
