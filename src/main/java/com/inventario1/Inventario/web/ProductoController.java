package com.inventario1.Inventario.web;

import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;

    // Detalle por código de barras: /productos/{codigoBarras}
    @GetMapping("/{codigoBarras}")
    public String detallePorCodigo(@PathVariable String codigoBarras, Model model) {
        var producto = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        model.addAttribute("producto", producto);
        return "detalles_productos"; // <- nombre exacto de la vista
    }

    // (Opcional) Detalle por ID, si alguna vez lo necesitas:
    @GetMapping("/id/{id}")
    public String detallePorId(@PathVariable Long id) {
        var prod = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        return "redirect:/productos/" + prod.getCodigoBarras();
    }
}
