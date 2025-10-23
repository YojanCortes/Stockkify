// Ubicación: src/main/java/com/inventario1/Inventario/controllers/AlertasController.java
package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/alertas")
@RequiredArgsConstructor
class AlertasController {

    final ProductoRepository productoRepository;

    static final int NEAR_MIN_RANGE = 5;

    @GetMapping
    String verAlertas(Model model) {
        List<Producto> base = productoRepository.findByActivoTrue();
        List<AlertaVM> alertas = buildAlertas(base);
        model.addAttribute("alertas", alertas);
        model.addAttribute("totalAlertas", alertas.size());
        model.addAttribute("alertasTop3", alertas.stream().limit(3).toList());
        return "alertas";
    }

    @ModelAttribute("totalAlertas")
    Integer totalAlertasAttr() {
        List<Producto> base = productoRepository.findByActivoTrue();
        return buildAlertas(base).size();
    }

    @ModelAttribute("alertasTop3")
    List<AlertaVM> alertasTop3Attr() {
        List<Producto> base = productoRepository.findByActivoTrue();
        return buildAlertas(base).stream().limit(3).toList();
    }

    static List<AlertaVM> buildAlertas(List<Producto> base) {
        return base.stream()
                .map(AlertasController::toVM)
                .filter(vm -> !"bg-success".equals(vm.getColor()))
                .sorted(Comparator.comparingInt(AlertaVM::getStock))
                .toList();
    }

    static AlertaVM toVM(Producto p) {
        int sa = nvl(p.getStockActual());
        int sm = nvl(p.getStockMinimo());

        String color;
        String estado;

        if (sa <= sm) { color = "bg-danger"; estado = "Crítico"; }
        else if (sa <= sm + NEAR_MIN_RANGE) { color = "bg-warning"; estado = "Cercano al mínimo"; }
        else { color = "bg-success"; estado = "OK"; }

        AlertaVM vm = new AlertaVM();
        vm.setId(p.getId());
        vm.setNombreProducto(p.getNombre());
        vm.setCodigoBarras(p.getCodigoBarras());
        vm.setStock(sa);
        vm.setColor(color);
        vm.setEstado(estado);
        vm.setActivo(true);
        if (p.getCodigoBarras() != null && !p.getCodigoBarras().isBlank()) {
            vm.setImagenUrl("/img/productos/" + p.getCodigoBarras());
        }
        return vm;
    }

    static int nvl(Integer v) { return v == null ? 0 : v; }

    @Data
    static class AlertaVM {
        Long id;
        String nombreProducto;
        String codigoBarras;
        Integer stock;
        String color;
        String estado;
        String imagenUrl;
        boolean activo;
    }
}
