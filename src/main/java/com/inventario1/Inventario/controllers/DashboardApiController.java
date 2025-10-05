package com.inventario1.Inventario.controllers;



import com.inventario1.Inventario.services.DashboardService;
import com.inventario1.Inventario.services.dto.KpiRupturaDto;
import com.inventario1.Inventario.services.dto.ProductoSinStockDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final DashboardService service;

    public DashboardApiController(DashboardService service) {
        this.service = service;
    }

    /** Resumen KPI 3: activos, sinStock, porcentaje */
    @GetMapping("/kpi3")
    public KpiRupturaDto kpi3() {
        return service.obtenerKpiRuptura();
    }

    /** Detalle: lista de productos sin stock */
    @GetMapping("/kpi3/detalle")
    public List<ProductoSinStockDto> kpi3Detalle() {
        return service.obtenerProductosSinStock();
    }
}
