package com.inventario1.Inventario.services;

import com.inventario1.Inventario.repos.DashboardRepository;

// KPI 3
import com.inventario1.Inventario.services.dto.KpiRupturaDto;
import com.inventario1.Inventario.services.dto.ProductoSinStockDto;

// KPI 4
import com.inventario1.Inventario.services.dto.KpiValorInventarioDto;
import com.inventario1.Inventario.services.dto.KpiValorInventarioDto.CategoriaValorDto;

// KPI 7
import com.inventario1.Inventario.services.dto.TopProductoDto;

// KPI 8
import com.inventario1.Inventario.services.dto.SemanalPuntoDto;
import com.inventario1.Inventario.services.dto.KpiConsumoForecastDto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardRepository repo;

    public DashboardService(DashboardRepository repo) {
        this.repo = repo;
    }

    /* =========================================
       KPI 3 — Nivel de Rupturas de Stock (%)
       ========================================= */
    public KpiRupturaDto obtenerKpiRuptura() {
        long activos = repo.countProductosActivos();
        long sinStock = repo.countProductosSinStock();

        double porcentaje = 0.0;
        if (activos > 0) {
            porcentaje = (sinStock * 100.0) / activos;
            porcentaje = Math.round(porcentaje * 10.0) / 10.0; // 1 decimal para UI
        }

        return new KpiRupturaDto(activos, sinStock, porcentaje);
    }

    public List<ProductoSinStockDto> obtenerProductosSinStock() {
        List<Object[]> rows = repo.listarProductosSinStock();
        List<ProductoSinStockDto> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long id             = r[0] != null ? ((Number) r[0]).longValue() : null;
            String codigoBarras = r[1] != null ? r[1].toString() : null;
            String nombre       = r[2] != null ? r[2].toString() : null;
            String categoria    = r[3] != null ? r[3].toString() : null;
            Integer stockActual = r[4] != null ? ((Number) r[4]).intValue() : 0;
            out.add(new ProductoSinStockDto(id, codigoBarras, nombre, categoria, stockActual));
        }
        return out;
    }

    /* =========================================
       KPI 4 — Valor Total de Inventario (CLP)
       ========================================= */
    public KpiValorInventarioDto obtenerKpiValorInventario() {
        // Σ (precio × cantidad) de productos activos (stock truncado >= 0 en el query)
        BigDecimal totalValorBD = repo.valorTotalInventarioCLP();
        long totalValor = (totalValorBD != null) ? totalValorBD.longValue() : 0L;

        // Σ cantidades (stock) de productos activos
        Long ti = repo.totalItemsInventario();
        long totalItems = (ti != null) ? ti : 0L;

        long promedioPorItem = (totalItems > 0)
                ? Math.round((double) totalValor / (double) totalItems)
                : 0L;

        // Detalle por categoría
        List<Object[]> rows = repo.valorInventarioPorCategoria();
        List<CategoriaValorDto> categorias = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            String categoria = r[0] != null ? r[0].toString() : "Sin categoría";
            long valor = 0L;
            Object v = r[1];
            if (v instanceof BigDecimal bd) valor = bd.longValue();
            else if (v instanceof Number n) valor = n.longValue();
            categorias.add(new CategoriaValorDto(categoria, valor));
        }

        return new KpiValorInventarioDto(
                totalItems,
                promedioPorItem,
                totalValor,
                categorias
        );
    }

    /* =========================================
       KPI 7 — Top 5 Productos Más Vendidos
       ========================================= */
    public List<TopProductoDto> obtenerTop5Vendidos(LocalDate desde, LocalDate hasta) {
        // Por defecto: últimos 30 días [hoy-29, hoy]
        LocalDate h = (hasta != null) ? hasta : LocalDate.now();
        LocalDate d = (desde != null) ? desde : h.minusDays(29);

        LocalDateTime ini = d.atStartOfDay();
        LocalDateTime fin = h.atTime(LocalTime.MAX);

        List<Object[]> rows = repo.top5VendidosEntre(ini, fin);
        List<TopProductoDto> out = new ArrayList<>(rows.size());

        for (Object[] r : rows) {
            // Soporta ambas variantes de la query:
            // (1) codigo, nombre, unidades
            // (2) codigo, nombre, categoria, unidades
            String codigo    = r[0] != null ? r[0].toString() : null;
            String nombre    = r[1] != null ? r[1].toString() : null;
            String categoria = null;
            long unidades;

            if (r.length >= 4) {
                categoria = r[2] != null ? r[2].toString() : null;
                unidades  = (r[3] instanceof Number n) ? n.longValue() : 0L;
            } else {
                unidades  = (r[2] instanceof Number n) ? n.longValue() : 0L;
            }
            out.add(new TopProductoDto(codigo, nombre, categoria, unidades));
        }
        return out;
    }

    /* =========================================
       KPI 8 — Consumo Estimado Semanal (IA)
       ========================================= */

    /**
     * Serie semanal (histórico) de consumo: suma de cantidades SALIDA por semana ISO.
     * @param semanas cuántas semanas hacia atrás (ej. 52)
     */
    public List<SemanalPuntoDto> obtenerConsumoSemanal(int semanas) {
        int n = Math.max(1, semanas);
        List<Object[]> rows = repo.consumoSemanalUltimas(n);

        List<SemanalPuntoDto> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            // r[0] = anioSemana (no se usa)
            // r[1] = semanaInicioAprox (YYYY-MM-DD)
            // r[2] = unidades
            LocalDate inicio = r[1] != null ? LocalDate.parse(r[1].toString()) : null;
            long unidades = (r[2] instanceof Number num) ? num.longValue() : 0L;
            if (inicio != null) out.add(new SemanalPuntoDto(inicio, unidades));
        }
        // La query viene DESC: invertimos a ascendente (mejor para graf/forecast)
        Collections.reverse(out);
        return out;
    }

    /** Pronóstico semanal usando Holt con tendencia amortiguada. */
    public KpiConsumoForecastDto obtenerConsumoSemanalForecast(int semanasHist, int horizonte) {
        List<SemanalPuntoDto> hist = obtenerConsumoSemanal(semanasHist);
        if (hist.isEmpty()) {
            return new KpiConsumoForecastDto(hist, List.of());
        }
        List<SemanalPuntoDto> forecast = holtDampedForecast(hist, horizonte, 0.4, 0.2, 0.98);
        return new KpiConsumoForecastDto(hist, forecast);
    }

    /** Holt (nivel + tendencia amortiguada). */
    private List<SemanalPuntoDto> holtDampedForecast(
            List<SemanalPuntoDto> serie,
            int h,
            double alpha, double beta, double phi
    ) {
        int n = serie.size();
        List<SemanalPuntoDto> out = new ArrayList<>(Math.max(0, h));
        if (n == 0 || h <= 0) return out;

        // valores
        List<Long> y = new ArrayList<>(n);
        for (SemanalPuntoDto p : serie) y.add(Math.max(0L, p.unidades()));

        // inicialización
        double level, trend;
        if (n >= 2) {
            level = y.get(0);
            trend = y.get(1) - y.get(0);
        } else {
            level = y.get(0);
            trend = 0.0;
        }

        // ajuste
        for (int t = 1; t < n; t++) {
            double yt = y.get(t);
            double prevLevel = level;
            level = alpha * yt + (1 - alpha) * (level + phi * trend);
            trend = beta * (level - prevLevel) + (1 - beta) * (phi * trend);
        }

        // base fecha
        LocalDate last = serie.get(n - 1).semanaInicio();

        // pronóstico
        double sumPhi = 0.0;
        for (int k = 1; k <= h; k++) {
            sumPhi = phi * sumPhi + 1.0;          // acumula phi^1 + ... + phi^k
            double fk = level + sumPhi * trend;   // nivel + tendencia amortiguada
            long val = Math.max(0L, Math.round(fk));
            out.add(new SemanalPuntoDto(last.plusWeeks(k), val));
        }
        return out;
    }
}
