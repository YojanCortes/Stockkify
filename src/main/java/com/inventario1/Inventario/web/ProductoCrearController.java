package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import com.inventario1.Inventario.web.dto.ProductoCrearForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/productos/legacy")
class ProductoCrearController {

    final ProductoRepository productoRepository;

    @GetMapping("/agregar")
    String agregarForm(Model model) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new ProductoCrearForm());
        return "agregar_producto";
    }

    @PostMapping(path = "/agregar")
    String crear(@Valid @ModelAttribute("form") ProductoCrearForm form,
                 BindingResult br,
                 RedirectAttributes ra,
                 Model model) {

        if (!br.hasErrors() && productoRepository.existsByCodigoBarras(form.getCodigoBarras())) {
            br.rejectValue("codigoBarras", "exists", "Ya existe un producto con ese código de barras.");
        }
        if (br.hasErrors()) return "agregar_producto";

        Producto p = new Producto();
        p.setNombre(form.getNombre() != null ? form.getNombre().trim() : null);
        p.setMarca(form.getMarca());
        p.setCategoria(form.getCategoria());
        p.setUnidadBase(form.getUnidadBase());
        p.setVolumenNominalMl(form.getVolumenNominalMl());
        p.setGraduacionAlcoholica(form.getGraduacionAlcoholica());
        p.setFechaVencimiento(form.getFechaVencimiento());
        p.setStockActual(form.getStockActual() != null ? form.getStockActual() : 0);
        p.setStockMinimo(form.getStockMinimo() != null ? form.getStockMinimo() : 0);
        p.setCodigoBarras(form.getCodigoBarras());
        p.setPerecible(Boolean.TRUE.equals(form.getPerecible()));
        p.setRetornable(Boolean.TRUE.equals(form.getRetornable()));
        p.setActivo(form.getActivo() == null || form.getActivo());

        p = productoRepository.save(p);

        if (form.getImagen() != null && !form.getImagen().isEmpty()) {
            try {
                p.setImagen(form.getImagen().getBytes());
                p.setImagenContentType(form.getImagen().getContentType());
                p.setImagenNombre(form.getImagen().getOriginalFilename());
                p.setImagenTamano(form.getImagen().getSize());
                productoRepository.save(p);
            } catch (IOException e) {
                log.warn("No se pudo guardar la imagen del producto {}: {}", p.getCodigoBarras(), e.getMessage());
                ra.addFlashAttribute("warn", "Producto creado, pero la imagen no pudo guardarse.");
            }
        }

        ra.addFlashAttribute("ok", "Producto creado correctamente (legacy).");
        return "redirect:/productos/" + p.getCodigoBarras();
    }
}
