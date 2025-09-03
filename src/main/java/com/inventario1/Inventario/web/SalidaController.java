package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.services.InventarioService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/salidas")
public class SalidaController {

    private final InventarioService inventarioService;

    public SalidaController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    // /salidas  y  /salidas/  --> redirige al formulario
    @GetMapping({"", "/"})
    public String root() {
        return "redirect:/salidas/nueva";
    }

    // /salidas/nueva  y  /salidas/nueva/
    @GetMapping({"/nueva", "/nueva/"})
    public String nueva(@RequestParam(value = "codigo", required = false) String codigo, Model model) {
        if (codigo != null && !codigo.isBlank()) {
            String cod = codigo.trim();
            try {
                Producto p = inventarioService.leerProducto(cod);
                model.addAttribute("producto", p);
                model.addAttribute("codigo", p.getCodigoBarras());
            } catch (EntityNotFoundException e) {
                model.addAttribute("error", e.getMessage());
                model.addAttribute("codigo", cod);
            }
        }
        return "salida_nueva";
    }

    // Registrar salida
    @PostMapping
    public String crear(@RequestParam("codigo") String codigo,
                        @RequestParam("cantidad") Integer cantidad,
                        @RequestParam(value = "motivo", required = false) String motivo,
                        @RequestParam(value = "referencia", required = false) String referencia,
                        @RequestParam(value = "usuario", required = false) String usuario,
                        Model model) {
        try {
            if (codigo == null || codigo.isBlank()) {
                throw new IllegalArgumentException("Debes indicar el código de barras.");
            }
            if (cantidad == null || cantidad <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor que 0.");
            }

            var mov = inventarioService.registrarSalida(codigo.trim(), cantidad, motivo, referencia, usuario);
            model.addAttribute("ok", "Salida registrada. Stock resultante: " + mov.getStockResultante());

            Producto p = inventarioService.leerProducto(codigo.trim());
            model.addAttribute("producto", p);
            model.addAttribute("codigo", p.getCodigoBarras());
            model.addAttribute("cantidad", null);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            try {
                Producto p = inventarioService.leerProducto(codigo.trim());
                model.addAttribute("producto", p);
                model.addAttribute("codigo", p.getCodigoBarras());
            } catch (Exception ignore) { }
        }
        return "salida_nueva";
    }
}
