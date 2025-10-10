package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.services.DashboardService;
import com.inventario1.Inventario.services.dto.KpiRupturaDto;
import com.inventario1.Inventario.services.dto.ProductoSinStockDto;
import com.inventario1.Inventario.services.dto.KpiValorInventarioDto;
import com.inventario1.Inventario.services.dto.TopProductoDto;
import com.inventario1.Inventario.services.dto.KpiConsumoForecastDto;
import com.inventario1.Inventario.services.dto.SemanalPuntoDto;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin
public class DashboardApiController {

    private final DashboardService service;

    public DashboardApiController(DashboardService service) {
        this.service = service;
    }

    /* =======================
       KPI 3 — Rupturas (%)
       ======================= */
    @GetMapping(value = "/kpi3", produces = "application/json")
    public KpiRupturaDto kpi3() { return service.obtenerKpiRuptura(); }

    @GetMapping(value = "/kpi3/detalle", produces = "application/json")
    public List<ProductoSinStockDto> kpi3Detalle() { return service.obtenerProductosSinStock(); }

    /* ============================================
       KPI 4 — Valor Total de Inventario (CLP)
       ============================================ */
    @GetMapping(value = "/kpi4", produces = "application/json")
    public KpiValorInventarioDto kpi4() { return service.obtenerKpiValorInventario(); }

    /* ============================================
       KPI 7 — Top 5 Productos Más Vendidos
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
    @GetMapping(value = "/kpi8", produces = "application/json")
    public KpiConsumoForecastDto kpi8(
            @RequestParam(name = "semanas", defaultValue = "52") int semanasHist,
            @RequestParam(name = "h",       defaultValue = "8")  int horizonte
    ) {
        semanasHist = Math.max(1, Math.min(semanasHist, 260));
        horizonte   = Math.max(1, Math.min(horizonte, 26));
        return service.obtenerConsumoSemanalForecast(semanasHist, horizonte);
    }

    @GetMapping(value = "/kpi8/serie", produces = "application/json")
    public List<SemanalPuntoDto> kpi8Serie(
            @RequestParam(name = "semanas", defaultValue = "52") int semanasHist
    ) {
        semanasHist = Math.max(1, Math.min(semanasHist, 260));
        return service.obtenerConsumoSemanal(semanasHist);
    }

    /* =========================================
       BAJA ROTACIÓN — para baja-rotacion.html
       ========================================= */
    @GetMapping(value = "/baja-rotacion", produces = "application/json")
    public Map<String, Object> bajaRotacion(
            @RequestParam(name = "umbral", defaultValue = "5") Integer umbral,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "categoria", required = false) String categoria,
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "50") Integer size
    ) {
        return service.bajaRotacion(umbral, desde, hasta, categoria, page, size);
    }

    /* =========================================
       KPI 1 — Rotación mensual
       ========================================= */
    @GetMapping(value = "/kpi1", produces = "application/json")
    public Map<String, Object> kpi1() { return service.kpiRotacionMensual(); }

    /* =======================
       KPI 2 — Stock de seguridad
       ======================= */
    @GetMapping(value = "/kpi2", produces = "application/json")
    public Map<String, Object> kpi2(
            @RequestParam(name = "mes", required = false) String mesYYYYMM,
            @RequestParam(name = "srv", defaultValue = "95") Integer servicioPct,
            @RequestParam(name = "lead", defaultValue = "7") Integer leadDias,
            @RequestParam(name = "categoria", required = false) String categoria
    ) {
        return service.kpiStockSeguridad(mesYYYYMM, servicioPct, leadDias, categoria);
    }

    @GetMapping(value = "/kpi2/detalle", produces = "application/json")
    public List<Map<String, Object>> kpi2Detalle(
            @RequestParam(name = "mes", required = false) String mesYYYYMM,
            @RequestParam(name = "srv", defaultValue = "95") Integer servicioPct,
            @RequestParam(name = "lead", defaultValue = "7") Integer leadDias,
            @RequestParam(name = "categoria", required = false) String categoria
    ) {
        return service.kpiStockSeguridadDetalle(mesYYYYMM, servicioPct, leadDias, categoria);
    }

    /* =========================================
       KPI 6 — Stock Obsoleto (> X días sin venta)
       ========================================= */
    @GetMapping(value = "/kpi6", produces = "application/json")
    public Map<String, Object> kpi6(@RequestParam(name = "dias", defaultValue = "60") Integer dias) {
        return service.kpiStockObsoleto(dias);
    }

    @GetMapping(value = "/kpi6/detalle", produces = "application/json")
    public Map<String, Object> kpi6Detalle(
            @RequestParam(name = "dias", defaultValue = "60") Integer dias,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size
    ) {
        return service.kpiStockObsoletoDetalle(dias, query, page, size);
    }

    /* =========================================
       KPI 10 — Rentabilidad (margen unitario)
       ========================================= */
    @GetMapping(value = "/kpi10", produces = "application/json")
    public Map<String, Object> kpi10(
            @RequestParam(name = "top", defaultValue = "10") Integer top,
            @RequestParam(name = "low", defaultValue = "10") Integer low
    ) {
        return service.kpiRentabilidad(top, low);
    }

    /* =========================================
       KPI 5 — Baja Rotación (tarjeta breve)
       Esperado: { umbral, total, muestra: [{nombre, ventasMensuales}] }
       ========================================= */
    @GetMapping(value = "/kpi5", produces = "application/json")
    public Map<String, Object> kpi5(
            @RequestParam(name = "umbral", defaultValue = "5") Integer umbral,
            @RequestParam(name = "categoria", required = false) String categoria
    ) {
        Map<String, Object> full = service.bajaRotacion(umbral, null, null, categoria, 1, 5);

        long total = 0L;
        Object totalObj = full.get("total");
        if (totalObj instanceof Number n) total = n.longValue();

        // productos es List<Map<String,Object>> en nuestro servicio;
        // lo tratamos de forma segura para evitar problemas de generics.
        List<Map<String, Object>> muestra = new ArrayList<>();
        Object prods = full.get("productos");
        if (prods instanceof List<?> list) {
            for (Object it : list) {
                if (it instanceof Map<?, ?> m) {
                    Object nom = m.get("nombre");
                    Object vm  = m.get("ventasMensuales");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("nombre", nom != null ? nom : "-");
                    row.put("ventasMensuales", vm != null ? vm : 0);
                    muestra.add(row);
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("umbral", umbral);
        out.put("total", total);
        out.put("muestra", muestra);
        return out;
    }

    /* =========================================
       KPI 9 — Alertas de stock (tarjeta breve)
       Esperado: { activas, ultimas: [{producto, nivel, estado}] }
       ========================================= */
    @GetMapping(value = "/kpi9", produces = "application/json")
    public Map<String, Object> kpi9() {
        List<ProductoSinStockDto> sinStock = service.obtenerProductosSinStock();
        int activas = sinStock.size();

        List<Map<String, Object>> ultimas = new ArrayList<>();
        for (int i = 0; i < Math.min(5, sinStock.size()); i++) {
            ProductoSinStockDto p = sinStock.get(i);
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("producto", getNombreSafe(p)); // evita acceso a campo privado
            x.put("nivel", "sin_stock");
            x.put("estado", "critico");
            ultimas.add(x);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activas", activas);
        out.put("ultimas", ultimas);
        return out;
    }

    /* =======================
       Utilidades
       ======================= */
    private static LocalDate parseFlexible(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s, DateTimeFormatter.ISO_DATE); }
        catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy")); }
        catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd-MM-yyyy")); }
        catch (DateTimeParseException ignored) {}
        return null;
    }

    /** Intenta POJO (getNombre) y record (nombre) para obtener el nombre sin romper la compilación. */
    private static String getNombreSafe(ProductoSinStockDto p) {
        try {
            return (String) p.getClass().getMethod("getNombre").invoke(p);
        } catch (Exception ignore) {
            try {
                return (String) p.getClass().getMethod("nombre").invoke(p);
            } catch (Exception ignore2) {
                return "-";
            }
        }
    }
}
