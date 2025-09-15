package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller                      // <- IMPORTANTE: @Controller (NO @RestController)
@RequiredArgsConstructor
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;

    @GetMapping("/{codigoBarras}") // /productos/5609876543212
    public String verPorCodigo(@PathVariable String codigoBarras, Model model) {
        Producto p = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("producto", p);
        return "detalles_productos"; // <- nombre exacto de la plantilla Thymeleaf
    }
    @PostMapping("/productos/{codigoBarras}/eliminar")
    public String eliminarProducto(@PathVariable String codigoBarras,
                                   RedirectAttributes redirect) {
        productoRepository.findByCodigoBarras(codigoBarras).ifPresentOrElse(p -> {
            productoRepository.delete(p);
            redirect.addFlashAttribute("ok", "Producto eliminado: " + codigoBarras);
        }, () -> {
            redirect.addFlashAttribute("error", "Producto no encontrado: " + codigoBarras);
        });

        // Volvé al listado principal (ajústalo si tu listado vive en otra ruta)
        return "redirect:/";
    }

}
