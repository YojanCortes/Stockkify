package com.inventario1.Inventario.controllers;


import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/alertas")
@RequiredArgsConstructor
public class AlertasController {

    private final ProductoRepository productoRepository;

    // Rango fijo de “cerca del mínimo” (amarillo)
    private static final int NEAR_MIN_RANGE = 5;

    @GetMapping
    public String verAlertas(Model model) {
        List<Producto> productos = productoRepository.findAll();

        List<AlertaVM> alertas = productos.stream()
                .map(p -> {
                    int sa = nvl(p.getStockActual());
                    int sm = nvl(p.getStockMinimo());

                    String color;
                    String estado;

                    if (sa <= sm) {
                        color = "bg-danger";
                        estado = "Crítico";
                    } else if (sa <= sm + NEAR_MIN_RANGE) {
                        color = "bg-warning";
                        estado = "Cercano al mínimo";
                    } else {
                        color = "bg-success";
                        estado = "OK";
                    }

                    AlertaVM vm = new AlertaVM();
                    vm.setId(p.getId());
                    vm.setNombreProducto(p.getNombre());
                    vm.setCodigoBarras(p.getCodigoBarras());
                    vm.setStock(sa);
                    vm.setColor(color);
                    vm.setEstado(estado);

                    // Si tienes endpoint de imagen por código de barras
                    if (p.getCodigoBarras() != null && !p.getCodigoBarras().isBlank()) {
                        vm.setImagenUrl("/productos/" + p.getCodigoBarras() + "/imagen");
                    }
                    return vm;
                })
                // Solo mostrar AMARILLOS o ROJOS
                .filter(vm -> !"bg-success".equals(vm.getColor()))
                // Ordenar de menor a mayor stock
                .sorted(Comparator.comparingInt(AlertaVM::getStock))
                .toList();

        model.addAttribute("alertas", alertas);
        return "alertas";
    }

    private static int nvl(Integer v) { return v == null ? 0 : v; }

    @Data
    public static class AlertaVM {
        private Long id;
        private String nombreProducto;
        private String codigoBarras;
        private Integer stock;
        private String color;     // bg-danger | bg-warning | bg-success
        private String estado;    // Crítico | Cercano al mínimo | OK
        private String imagenUrl; // opcional
    }
}
