package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.TipoMovimiento;
import com.inventario1.Inventario.repos.ProductoRepository;
import com.inventario1.Inventario.services.MovimientosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/productos/legacy") // base común
public class ProductoLegacyController {

    private static final Logger log = LoggerFactory.getLogger(ProductoLegacyController.class);

    private final ProductoRepository productoRepository;
    private final MovimientosService movimientosService;

    public ProductoLegacyController(ProductoRepository productoRepository,
                                    MovimientosService movimientosService) {
        this.productoRepository = productoRepository;
        this.movimientosService = movimientosService;
    }

    // ⚠️ Cambiado de "/actualizar-stock" a "/actualizar-stock-directo" para evitar colisión
    @PostMapping("/actualizar-stock-directo")
    @Transactional
    public String actualizarStockDirecto(@RequestParam("codigoBarras") String codigoBarras,
                                         @RequestParam("cantidad") int cantidad,
                                         RedirectAttributes ra) {

        Producto p = productoRepository.findByCodigoBarras(codigoBarras.trim())
                .orElseThrow(() -> new IllegalArgumentException("No existe producto con código: " + codigoBarras));

        int sumar = Math.max(0, cantidad);
        if (sumar == 0) {
            ra.addFlashAttribute("warn", "Cantidad 0: no se realizaron cambios.");
            return "redirect:/productos/" + p.getCodigoBarras();
        }

        int actual = p.getStockActual() == null ? 0 : p.getStockActual();
        p.setStockActual(actual + sumar);
        productoRepository.save(p);

        log.info("Registrando movimiento ENTRADA: cb={}, cantidad={}", p.getCodigoBarras(), sumar);
        movimientosService.registrarMovimiento(
                p, TipoMovimiento.ENTRADA, sumar,
                "UI: actualizar stock (directo)", null
        );

        ra.addFlashAttribute("ok", "Stock actualizado (+ " + sumar + ") y movimiento registrado.");
        return "redirect:/productos/" + p.getCodigoBarras();
    }
}
