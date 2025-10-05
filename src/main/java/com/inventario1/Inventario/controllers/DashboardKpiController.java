package com.inventario1.Inventario.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardKpiController {

    // KPI 3 — Nivel de Rupturas de Stock (%)
    @GetMapping("/dashboard/rupturas")
    public String kpiRupturas() {
        // Renderiza: src/main/resources/templates/dashboard/rupturas.html
        return "dashboard/rupturas";
    }

    // KPI 4 — Valor Total de Inventario (CLP)
    @GetMapping("/dashboard/valor-inventario")
    public String kpiValorInventario() {
        // Renderiza: src/main/resources/templates/dashboard/valor-inventario.html
        return "dashboard/valor-inventario";
    }

    // KPI 7 — Top 5 Productos Más Vendidos
    @GetMapping("/dashboard/top-vendidos")
    public String kpiTopVendidos() {
        // Renderiza: src/main/resources/templates/dashboard/top-vendidos.html
        return "dashboard/top-vendidos";
    }

    // KPI 8 — Consumo Estimado Semanal (IA)
    @GetMapping("/dashboard/consumo-ia")
    public String kpiConsumoIA() {
        // Renderiza: src/main/resources/templates/dashboard/consumo-ia.html
        return "dashboard/consumo-ia";
    }
}
