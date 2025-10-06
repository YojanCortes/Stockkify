package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.services.DashboardService;
import com.inventario1.Inventario.services.dto.KpiRupturaDto;
import com.inventario1.Inventario.services.dto.ProductoSinStockDto;
import com.inventario1.Inventario.services.dto.KpiValorInventarioDto;
import com.inventario1.Inventario.services.dto.TopProductoDto;
import com.inventario1.Inventario.services.dto.KpiConsumoForecastDto;
import com.inventario1.Inventario.services.dto.SemanalPuntoDto;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin // quítalo si todo corre en el mismo origen
public class DashboardApiController {

    private final DashboardService service;

    public DashboardApiController(DashboardService service) {
        this.service = service;
    }

    /* =======================
       KPI 3 — Rupturas (%)
       ======================= */

    /** Resumen KPI 3: activos, sinStock, porcentaje */
    @GetMapping(value = "/kpi3", produces = "application/json")
    public KpiRupturaDto kpi3() {
        return service.obtenerKpiRuptura();
    }

    /** Detalle: lista de productos sin stock */
    @GetMapping(value = "/kpi3/detalle", produces = "application/json")
    public List<ProductoSinStockDto> kpi3Detalle() {
        return service.obtenerProductosSinStock();
    }

    /* ============================================
       KPI 4 — Valor Total de Inventario (CLP)
       ============================================ */

    @GetMapping(value = "/kpi4", produces = "application/json")
    public KpiValorInventarioDto kpi4() {
        return service.obtenerKpiValorInventario();
    }

    /* ============================================
       KPI 7 — Top 5 Productos Más Vendidos
       Acepta fechas en YYYY-MM-DD, dd/MM/yyyy o dd-MM-yyyy
       ============================================ */

    @GetMapping(value = "/kpi7", produces = "application/json")
    public List<TopProductoDto> kpi7Top5(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta
    ) {
        LocalDate d = parseFlexible(desde);
        LocalDate h = parseFlexible(hasta);
        return service.obtenerTop5Vendidos(d, h);
    }

    /* ============================================
       KPI 8 — Consumo Estimado Semanal (IA)
       ============================================ */

    /** Histórico + pronóstico semanal (Holt amortiguado). */
    @GetMapping(value = "/kpi8", produces = "application/json")
    public KpiConsumoForecastDto kpi8(
            @RequestParam(name = "semanas", defaultValue = "52") int semanasHist,
            @RequestParam(name = "h",       defaultValue = "8")  int horizonte
    ) {
        // límites simples para evitar valores extremos
        semanasHist = Math.max(1, Math.min(semanasHist, 260)); // hasta 5 años
        horizonte   = Math.max(1, Math.min(horizonte, 26));    // hasta medio año
        return service.obtenerConsumoSemanalForecast(semanasHist, horizonte);
    }

    /** Solo la serie histórica semanal (sin forecast). */
    @GetMapping(value = "/kpi8/serie", produces = "application/json")
    public List<SemanalPuntoDto> kpi8Serie(
            @RequestParam(name = "semanas", defaultValue = "52") int semanasHist
    ) {
        semanasHist = Math.max(1, Math.min(semanasHist, 260));
        return service.obtenerConsumoSemanal(semanasHist);
    }


    /* =======================
       Utilidades
       ======================= */




    /** Parsea YYYY-MM-DD, dd/MM/yyyy o dd-MM-yyyy. Si falla, retorna null. */
    private static LocalDate parseFlexible(String s) {
        if (s == null || s.isBlank()) return null;

        // 1) ISO 8601 (YYYY-MM-DD)
        try { return LocalDate.parse(s, DateTimeFormatter.ISO_DATE); }
        catch (DateTimeParseException ignored) {}

        // 2) dd/MM/yyyy
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy")); }
        catch (DateTimeParseException ignored) {}

        // 3) dd-MM-yyyy
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd-MM-yyyy")); }
        catch (DateTimeParseException ignored) {}

        // Si no parsea, devolvemos null y el service usará últimos 30 días (KPI 7)
        return null;
    }
}
