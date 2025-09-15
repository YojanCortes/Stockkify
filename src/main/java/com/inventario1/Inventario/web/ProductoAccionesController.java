package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // <-- IMPORT CORRECTO

@Controller
@RequiredArgsConstructor
@RequestMapping("/productos")
public class ProductoAccionesController {

    private final ProductoRepository productoRepository;

    @PostMapping("/{codigoBarras}/eliminar")
    public String eliminar(@PathVariable String codigoBarras,
                           RedirectAttributes ra) { // <-- TIPO CORRECTO

        Producto prod = productoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        productoRepository.delete(prod); // (o borrado lógico)

        ra.addFlashAttribute("ok", "Producto eliminado"); // <-- ahora compila
        return "redirect:/";
    }

    @PostMapping("/{codigoBarras}/imagen")
    public String actualizarImagen(@PathVariable String codigoBarras,
                                   @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                   RedirectAttributes ra) { // <-- también aquí
        // ... tu lógica de guardado ...
        ra.addFlashAttribute("ok", "Imagen actualizada.");
        return "redirect:/productos/" + codigoBarras;
    }
}
