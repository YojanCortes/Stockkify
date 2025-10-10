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
import java.time.temporal.ChronoUnit;
import java.util.*;

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
        BigDecimal totalValorBD = repo.valorTotalInventarioCLP();
        long totalValor = (totalValorBD != null) ? totalValorBD.longValue() : 0L;

        Long ti = repo.totalItemsInventario();
        long totalItems = (ti != null) ? ti : 0L;

        long promedioPorItem = (totalItems > 0)
                ? Math.round((double) totalValor / (double) totalItems)
                : 0L;

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
        LocalDate h = (hasta != null) ? hasta : LocalDate.now();
        LocalDate d = (desde != null) ? desde : h.minusDays(29);

        LocalDateTime ini = d.atStartOfDay();
        LocalDateTime fin = h.atTime(LocalTime.MAX);

        List<Object[]> rows = repo.top5VendidosEntre(ini, fin);
        List<TopProductoDto> out = new ArrayList<>(rows.size());

        for (Object[] r : rows) {
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

    /** Serie semanal (histórico) de consumo. */
    public List<SemanalPuntoDto> obtenerConsumoSemanal(int semanas) {
        int n = Math.max(1, semanas);
        List<Object[]> rows = repo.consumoSemanalUltimas(n);

        List<SemanalPuntoDto> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            LocalDate inicio = r[1] != null ? LocalDate.parse(r[1].toString()) : null;
            long unidades = (r[2] instanceof Number num) ? num.longValue() : 0L;
            if (inicio != null) out.add(new SemanalPuntoDto(inicio, unidades));
        }
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

        List<Long> y = new ArrayList<>(n);
        for (SemanalPuntoDto p : serie) y.add(Math.max(0L, p.unidades()));

        double level, trend;
        if (n >= 2) {
            level = y.get(0);
            trend = y.get(1) - y.get(0);
        } else {
            level = y.get(0);
            trend = 0.0;
        }

        for (int t = 1; t < n; t++) {
            double yt = y.get(t);
            double prevLevel = level;
            level = alpha * yt + (1 - alpha) * (level + phi * trend);
            trend = beta * (level - prevLevel) + (1 - beta) * (phi * trend);
        }

        LocalDate last = serie.get(n - 1).semanaInicio();

        double sumPhi = 0.0;
        for (int k = 1; k <= h; k++) {
            sumPhi = phi * sumPhi + 1.0;
            double fk = level + sumPhi * trend;
            long val = Math.max(0L, Math.round(fk));
            out.add(new SemanalPuntoDto(last.plusWeeks(k), val));
        }
        return out;
    }

    // =========================================
    // KPI 2 — Stock de Seguridad
    // =========================================
    public Map<String, Object> kpiStockSeguridad(String mesYYYYMM, Integer servicioPct, Integer leadDias, String categoria) {
        LocalDate[] rango = resolverMesRango(mesYYYYMM);
        LocalDate desde = rango[0];
        LocalDate hasta = rango[1];
        int diasRango = (int) java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1;

        double z = zFromServicio(servicioPct != null ? servicioPct : 95);
        int lead = leadDias != null && leadDias > 0 ? leadDias : 0;

        List<Object[]> rows = repo.demandaHistoricaTotales(desde, hasta, normalizaCat(categoria));

        long totalActual = 0L;
        long totalMinimo = 0L;

        for (Object[] r : rows) {
            long stockActual = asLong(r[4]);        // v.stock_actual
            long total = asLong(r[5]);              // SUM(qty)
            long sumsq = asLong(r[6]);              // SUM(qty^2)

            totalActual += stockActual;

            long minimo = calcularMinimoSeguridad(total, sumsq, diasRango, z, lead);
            totalMinimo += Math.max(0L, minimo);
        }

        double pct = totalMinimo > 0 ? (totalActual * 100.0 / totalMinimo) : 100.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mes", String.format("%04d-%02d", desde.getYear(), desde.getMonthValue()));
        out.put("stockActual", totalActual);
        out.put("stockMinimo", totalMinimo);
        out.put("porcentaje", Math.round(pct * 10.0) / 10.0);
        return out;
    }

    public List<Map<String, Object>> kpiStockSeguridadDetalle(String mesYYYYMM, Integer servicioPct, Integer leadDias, String categoria) {
        LocalDate[] rango = resolverMesRango(mesYYYYMM);
        LocalDate desde = rango[0];
        LocalDate hasta = rango[1];
        int diasRango = (int) java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1;

        double z = zFromServicio(servicioPct != null ? servicioPct : 95);
        int lead = leadDias != null && leadDias > 0 ? leadDias : 0;

        List<Object[]> rows = repo.demandaHistoricaTotales(desde, hasta, normalizaCat(categoria));
        List<Map<String, Object>> detalle = new ArrayList<>(rows.size());

        for (Object[] r : rows) {
            String codigo = asStr(r[1]);
            String nombre = asStr(r[2]);
            String cat    = asStr(r[3]);
            long stockAct = asLong(r[4]);
            long total    = asLong(r[5]);
            long sumsq    = asLong(r[6]);

            long minimo = calcularMinimoSeguridad(total, sumsq, diasRango, z, lead);
            minimo = Math.max(0L, minimo);

            double pct = minimo > 0 ? (stockAct * 100.0 / minimo) : 100.0;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigoBarras", codigo);
            m.put("nombre", nombre);
            m.put("categoria", cat != null ? cat : "GENERAL");
            m.put("stockActual", stockAct);
            m.put("stockMinimo", minimo);
            m.put("porcentaje", Math.round(pct));
            detalle.add(m);
        }
        return detalle;
    }

    // ----- helpers KPI 2 -----
    private static long calcularMinimoSeguridad(long sum, long sumsq, int nDias, double z, int leadDias) {
        if (nDias <= 1 || leadDias <= 0) return 0L;

        // media diaria
        double mu = sum / (double) nDias;
        // varianza muestral (incluye días con 0 venta)
        double var = (sumsq - nDias * mu * mu) / (double) (nDias - 1);
        if (Double.isNaN(var) || var < 0) var = 0;

        double sigmaDia = Math.sqrt(var);
        double sigmaLead = sigmaDia * Math.sqrt(leadDias);

        long ss = Math.round(z * sigmaLead);
        if (ss < 0) ss = 0;
        return ss;
    }

    private static String normalizaCat(String c) {
        if (c == null) return null;
        String t = c.trim();
        return t.isEmpty() ? null : t;
    }

    private static String asStr(Object o) { return o != null ? o.toString() : null; }
    private static long asLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof java.math.BigDecimal bd) return bd.longValue();
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }
    private static double asDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof BigDecimal bd) return bd.doubleValue();
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception ignored) { return 0.0; }
    }
    private static double round1(double v){ return Math.round(v * 10.0) / 10.0; }

    private static double zFromServicio(int srv) {
        // aproximación típica
        if (srv >= 99) return 2.33;
        if (srv >= 97) return 1.88;
        if (srv >= 95) return 1.64;
        if (srv >= 90) return 1.28;
        return 1.04; // ≈85%
    }

    private static LocalDate[] resolverMesRango(String mesYYYYMM) {
        LocalDate hoy = LocalDate.now();
        int y = hoy.getYear();
        int m = hoy.getMonthValue();

        if (mesYYYYMM != null && !mesYYYYMM.isBlank()) {
            try {
                String[] p = mesYYYYMM.trim().split("-");
                y = Integer.parseInt(p[0]);
                m = Integer.parseInt(p[1]);
            } catch (Exception ignored) {}
        }
        LocalDate desde = LocalDate.of(y, m, 1);
        LocalDate hasta = desde.withDayOfMonth(desde.lengthOfMonth());
        return new LocalDate[]{desde, hasta};
    }

    /* =========================================
       KPI 6 — Stock Obsoleto (> X días sin venta)
       ========================================= */
    public Map<String, Object> kpiStockObsoleto(Integer dias) {
        int umbral = (dias == null || dias <= 0) ? 60 : dias;

        long total = repo.countProductosActivos();      // Denominador: todos los activos
        long obsoletos = repo.countObsoletos(umbral);   // Numerador: stock > 0 y última venta > umbral días (o nunca vendió)

        double porcentaje = (total > 0)
                ? Math.round((obsoletos * 1000.0) / total) / 10.0  // 1 decimal
                : 0.0;

        List<Object[]> rows = repo.obsoletoConteoPorCategoria(umbral);
        List<Map<String,Object>> categorias = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            String nombre = r[0] != null ? r[0].toString() : "GENERAL";
            long cnt = (r[1] instanceof Number n) ? n.longValue() : 0L;
            double pctCat = (obsoletos > 0) ? Math.round((cnt * 1000.0) / obsoletos) / 10.0 : 0.0;

            Map<String,Object> c = new LinkedHashMap<>();
            c.put("nombre", nombre);
            c.put("pct", pctCat);
            categorias.add(c);
        }

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("dias", umbral);
        out.put("obsoletos", obsoletos);
        out.put("total", total);
        out.put("porcentaje", porcentaje);
        out.put("categorias", categorias);
        return out;
    }

    /* =========================================
       KPI 1 — Rotación mensual (para rotacion.html)
       ========================================= */
    public Map<String, Object> kpiRotacionMensual() {
        var hoy = java.time.LocalDate.now();
        var inicioMes = hoy.withDayOfMonth(1);
        var inicioMesPrev = inicioMes.minusMonths(1);
        var finMesPrev = inicioMes.minusDays(1);

        var ventas = repo.ventasNetasEntre(inicioMes.atStartOfDay(), hoy.atTime(java.time.LocalTime.MAX));
        var bdInv = repo.valorTotalInventarioCLP();
        long inventario = (bdInv != null ? bdInv.longValue() : 0L);
        double rot = inventario > 0 ? (ventas != null ? ventas.doubleValue() : 0.0) / inventario : 0.0;

        var ventasPrev = repo.ventasNetasEntre(inicioMesPrev.atStartOfDay(), finMesPrev.atTime(java.time.LocalTime.MAX));
        long inventarioPrev = inventario; // proxy
        double rotPrev = inventarioPrev > 0 ? (ventasPrev != null ? ventasPrev.doubleValue() : 0.0) / inventarioPrev : 0.0;

        var locale = new java.util.Locale("es", "CL");
        String mesAct = hoy.getMonth().getDisplayName(java.time.format.TextStyle.FULL, locale);
        mesAct = Character.toUpperCase(mesAct.charAt(0)) + mesAct.substring(1) + " " + hoy.getYear();
        var prev = hoy.minusMonths(1);
        String mesPrev = prev.getMonth().getDisplayName(java.time.format.TextStyle.FULL, locale);
        mesPrev = Character.toUpperCase(mesPrev.charAt(0)) + mesPrev.substring(1) + " " + prev.getYear();

        var out = new LinkedHashMap<String, Object>();
        out.put("ventasNetas", ventas != null ? ventas.longValue() : 0L);
        out.put("inventarioPromedio", inventario);
        out.put("rotacion", rot);
        out.put("mesActual", mesAct);
        out.put("rotacionPrev", rotPrev);
        out.put("mesPrevio", mesPrev);
        return out;
    }

    /* =========================================
       BAJA ROTACIÓN — Endpoint HTML
       ========================================= */
    public Map<String, Object> bajaRotacion(Integer umbral,
                                            LocalDate desde,
                                            LocalDate hasta,
                                            String categoria,
                                            int page,
                                            int size) {
        if (umbral == null) umbral = 5;
        if (hasta == null)  hasta  = LocalDate.now();
        if (desde == null)  desde  = hasta.minusDays(29);
        if (size <= 0) size = 50;
        if (page <= 0) page = 1;

        final int diasRango = (int) Math.max(1, ChronoUnit.DAYS.between(desde, hasta) + 1);
        final String catNorm = (categoria == null || categoria.isBlank()) ? null : categoria.trim();

        long total = repo.countBajaRotacion(umbral, desde, hasta, diasRango, catNorm);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) size));
        int offset = (page - 1) * size;

        List<Object[]> rows = repo.bajaRotacionPage(umbral, desde, hasta, diasRango, catNorm, size, offset);
        List<Map<String,Object>> productos = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            String nombre        = r[0] != null ? r[0].toString() : "-";
            String categoriaOut  = r[1] != null ? r[1].toString() : "GENERAL";
            double ventasMens    = r[2] instanceof Number n2 ? n2.doubleValue() : 0.0;
            int stock            = r[3] instanceof Number n3 ? n3.intValue()    : 0;
            int diasSinVenta     = r[4] instanceof Number n4 ? n4.intValue()    : 0;
            double costoTotal    = r[5] instanceof Number n5 ? n5.doubleValue() : 0.0;

            Map<String,Object> m = new LinkedHashMap<>();
            m.put("nombre", nombre);
            m.put("categoria", categoriaOut);
            m.put("ventasMensuales", Math.round(ventasMens * 100.0) / 100.0);
            m.put("stock", stock);
            m.put("diasSinVenta", diasSinVenta);
            m.put("costoTotal", Math.rint(costoTotal));
            productos.add(m);
        }

        List<Object[]> cats = repo.bajaRotacionConteoPorCategoria(umbral, desde, hasta, diasRango, catNorm);
        List<Map<String,Object>> categorias = new ArrayList<>(cats.size());
        for (Object[] r : cats) {
            String nom = r[0] != null ? r[0].toString() : "GENERAL";
            long cnt   = r[1] instanceof Number n ? n.longValue() : 0L;
            double pct = (total > 0) ? Math.round((cnt * 100.0 / total)) : 0.0;
            Map<String,Object> c = new LinkedHashMap<>();
            c.put("nombre", nom);
            c.put("pct", pct);
            categorias.add(c);
        }

        List<Object[]> catSel = repo.catalogoCategoriasSimple();
        List<Map<String,Object>> catalogo = new ArrayList<>(catSel.size());
        for (Object[] r : catSel) {
            String nom = r[0] != null ? r[0].toString() : "GENERAL";
            Map<String,Object> c = new LinkedHashMap<>();
            c.put("nombre", nom);
            catalogo.add(c);
        }

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("umbral", umbral);
        out.put("total", total);
        out.put("page", page);
        out.put("totalPages", totalPages);
        out.put("productos", productos);
        out.put("categorias", categorias);
        out.put("catalogoCategorias", catalogo);
        return out;
    }

    // =========================================
    // KPI 6 — Stock Obsoleto (detalle paginado + búsqueda)
    // =========================================
    public Map<String, Object> kpiStockObsoletoDetalle(Integer dias, String query, int page, int size) {
        final int umbral = (dias == null || dias <= 0) ? 60 : dias;

        // normaliza búsqueda
        final String q = (query == null || query.trim().isEmpty()) ? null : query.trim().toLowerCase();

        // sanea paginación
        int p = Math.max(1, page);
        int s = Math.max(1, Math.min(size, 100));
        int offset = (p - 1) * s;

        // total con filtro
        long total = repo.obsoletoCountFiltro(umbral, q);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) s));

        // página
        List<Object[]> rows = repo.obsoletoPage(umbral, q, s, offset);
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            String codigo   = r[0] != null ? r[0].toString() : null;                     // codigoBarras
            String nombre   = r[1] != null ? r[1].toString() : "-";                      // nombre
            String categoria= r[2] != null ? r[2].toString() : "GENERAL";                // categoria
            int stock       = (r[3] instanceof Number n) ? n.intValue() : 0;             // stock
            int diasSV      = (r[4] instanceof Number n) ? n.intValue() : 0;             // diasSinVenta

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigoBarras", codigo);
            m.put("nombre", nombre);
            m.put("categoria", categoria);
            m.put("stock", stock);
            m.put("diasSinVenta", diasSV);
            items.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dias", umbral);
        out.put("query", q);
        out.put("page", p);
        out.put("size", s);
        out.put("total", total);
        out.put("totalPages", totalPages);
        out.put("items", items);
        return out;
    }

    /* =========================================
       KPI 10 — Rentabilidad (margen unitario)
       Respuesta esperada por /api/dashboard/kpi10:
       {
         margenPromedioPct: number,
         top: [{codigoBarras, nombre, categoria, margenPct}],
         low: [{codigoBarras, nombre, categoria, margenPct}]
       }
       ========================================= */
    public Map<String, Object> kpiRentabilidad(Integer topN, Integer lowN) {
        int topSize = (topN == null) ? 10 : Math.max(3, Math.min(20, topN));
        int lowSize = (lowN == null) ? 10 : Math.max(3, Math.min(20, lowN));

        boolean tieneCostoEnProductos = false;
        try {
            tieneCostoEnProductos = repo.hasColCostoProductos() > 0;
        } catch (Exception ignored) {
            // Si falla el chequeo, asumimos que NO está la columna
        }

        List<Map<String,Object>> items = new ArrayList<>();

        if (tieneCostoEnProductos) {
            // Puede devolver 5 columnas (codigo, nombre, categoria, precio, costo)
            // o 4 columnas (codigo, nombre, precio, costo) — sin categoria.
            for (Object[] r : repo.productosPrecioYCosto()) {
                String codigo = asStr(r[0]);
                String nombre = asStr(r[1]);

                String categoria;
                double precio;
                double costo;

                if (r.length >= 5) {
                    // [0]=codigo, [1]=nombre, [2]=categoria, [3]=precio, [4]=costo
                    categoria = asStr(r[2]);
                    precio    = asDouble(r[3]);
                    costo     = asDouble(r[4]);
                } else if (r.length == 4) {
                    // [0]=codigo, [1]=nombre, [2]=¿categoria o precio?, [3]=¿precio o costo?
                    // Detectamos por tipo: si r[2] es número, no hay categoria.
                    if (r[2] instanceof Number) {
                        categoria = "GENERAL";
                        precio    = asDouble(r[2]);
                        costo     = asDouble(r[3]);
                    } else {
                        categoria = asStr(r[2]);
                        precio    = asDouble(r[3]);
                        costo     = 0.0; // por seguridad
                    }
                } else {
                    // Formato inesperado: ignoramos fila
                    continue;
                }

                double margenPct = calcularMargenPct(precio, costo);
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("codigoBarras", codigo);
                m.put("nombre", nombre);
                m.put("categoria", (categoria != null && !categoria.isBlank()) ? categoria : "GENERAL");
                m.put("margenPct", round1(margenPct));
                items.add(m);
            }
        } else {
            // Puede devolver 4 columnas (codigo, nombre, categoria, precio)
            // o 3 columnas (codigo, nombre, precio) — sin categoria.
            final double DEFAULT_COST_FACTOR = 0.70; // Heurística
            for (Object[] r : repo.productosSoloPrecio()) {
                String codigo = asStr(r[0]);
                String nombre = asStr(r[1]);

                String categoria = "GENERAL";
                double precio;

                if (r.length >= 4) {
                    // [0]=codigo, [1]=nombre, [2]=categoria, [3]=precio
                    if (r[2] instanceof Number) {
                        // r[2] no es categoria, es precio: 4 col atípicas -> tomamos [2] como precio
                        precio    = asDouble(r[2]);
                        categoria = "GENERAL";
                    } else {
                        categoria = asStr(r[2]);
                        precio    = asDouble(r[3]);
                    }
                } else if (r.length == 3) {
                    // [0]=codigo, [1]=nombre, [2]=precio
                    precio = asDouble(r[2]);
                } else {
                    // Formato inesperado: ignoramos fila
                    continue;
                }

                double costoEstimado = Math.max(0.0, precio * DEFAULT_COST_FACTOR);
                double margenPct = calcularMargenPct(precio, costoEstimado);

                Map<String,Object> m = new LinkedHashMap<>();
                m.put("codigoBarras", codigo);
                m.put("nombre", nombre);
                m.put("categoria", (categoria != null && !categoria.isBlank()) ? categoria : "GENERAL");
                m.put("margenPct", round1(margenPct));
                items.add(m);
            }
        }

        // Margen promedio (sobre todos los items válidos)
        List<Double> margenesValidos = new ArrayList<>();
        for (Map<String,Object> it : items) {
            double mp = asDouble(it.get("margenPct"));
            if (!Double.isNaN(mp) && !Double.isInfinite(mp)) {
                margenesValidos.add(mp);
            }
        }
        double prom = 0.0;
        if (!margenesValidos.isEmpty()) {
            double sum = 0.0;
            for (double v : margenesValidos) sum += v;
            prom = round1(sum / margenesValidos.size());
        }

        // Top (desc) y Low (asc)
        List<Map<String,Object>> sortedDesc = new ArrayList<>(items);
        sortedDesc.sort(Comparator.comparingDouble(o -> -asDouble(o.get("margenPct"))));
        List<Map<String,Object>> sortedAsc  = new ArrayList<>(items);
        sortedAsc.sort(Comparator.comparingDouble(o -> asDouble(o.get("margenPct"))));

        List<Map<String,Object>> top  = sortedDesc.subList(0, Math.min(topSize, sortedDesc.size()));
        List<Map<String,Object>> low  = sortedAsc.subList(0, Math.min(lowSize, sortedAsc.size()));

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("margenPromedioPct", prom);
        out.put("top", new ArrayList<>(top));
        out.put("low", new ArrayList<>(low));
        return out;
    }

    private static double calcularMargenPct(double precio, double costo) {
        if (precio <= 0) return 0.0;
        return ((precio - costo) / precio) * 100.0;
    }
}
