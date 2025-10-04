package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.MovimientoInventario;
import com.inventario1.Inventario.models.MovimientoLinea;
import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.MovimientoInventarioRepository;
import com.inventario1.Inventario.repos.MovimientoLineaRepository;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Carga masiva de PRODUCTOS + stock inicial (idempotente por referencia INIT:<codigo>:<fecha>).
 * VERSIÓN SIN APACHE POI (solo CSV). Si tienes Excel, expórtalo a .csv.
 *
 * También acepta archivos de MOVIMIENTOS en CSV con columnas:
 *   fecha, prod_id, prod_name, tipo[I/S], cantidad
 * y los transforma a filas de productos (stock_inicial = entradas - salidas, no negativo).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkProductoLoaderService {

    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final MovimientoLineaRepository movimientoLineaRepository;

    /* ===================== Resultados ===================== */
    @Data @Builder
    public static class Result {
        private int totalRows;
        private int persistedRows;
        private int skippedRows;
        private boolean dryRun;
        private List<String> errors;
        public boolean hasErrors() { return errors != null && !errors.isEmpty(); }
    }

    /* ===================== Fila de producto ===================== */
    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class FilaProducto {
        private String codigoBarras;          // obligatorio
        private String nombreProducto;        // obligatorio
        private String presentacion;          // opcional
        private String categoria;             // opcional
        private BigDecimal costoUnitario;     // opcional
        private BigDecimal precioVenta;       // opcional
        private Integer stockInicial;         // opcional (>= 0)
        private LocalDate fechaStockInicial;  // opcional (YYYY-MM-DD)
        private String proveedor;             // opcional
        private Boolean activo;               // opcional
    }

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /* ===================== Punto de entrada desde el Controller ===================== */
    @Transactional
    public Result procesarDesdeArchivo(MultipartFile file, boolean dryRun) {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("upload").toLowerCase(Locale.ROOT);
        List<FilaProducto> filas;
        try (InputStream in = file.getInputStream()) {
            if (name.endsWith(".csv")) {
                filas = parseCsvSmart(in);      // CSV (productos o movimientos)
            } else {
                throw new IllegalArgumentException(
                        "Este backend está configurado sin Apache POI. " +
                                "Convierte tu archivo a CSV (.csv) y vuelve a cargarlo."
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer el archivo: " + e.getMessage(), e);
        }
        return procesar(filas, dryRun);
    }

    /* ===================== Núcleo de negocio ===================== */
    @Transactional
    public Result procesar(List<FilaProducto> filas, boolean dryRun) {
        int total = 0, ok = 0, skip = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < filas.size(); i++) {
            total++;
            FilaProducto f = filas.get(i);
            try {
                validarFila(f);

                // 1) Upsert Producto
                Producto p = productoRepository.findByCodigoBarras(f.getCodigoBarras())
                        .orElseGet(() -> {
                            Producto np = new Producto();
                            np.setCodigoBarras(f.getCodigoBarras());
                            np.setNombre(f.getNombreProducto());
                            return np;
                        });

                // Campos opcionales (si vienen; si no, quedan null)
                if (nonBlank(f.getNombreProducto())) p.setNombre(f.getNombreProducto());
                if (nonBlank(f.getPresentacion()))  setIfPresentacion(p, f.getPresentacion());
                if (f.getCostoUnitario() != null)   setIfCosto(p, f.getCostoUnitario());
                if (f.getPrecioVenta() != null)     setIfPrecio(p, f.getPrecioVenta());
                if (nonBlank(f.getCategoria()))     setIfCategoria(p, f.getCategoria());
                if (nonBlank(f.getProveedor()))     setIfProveedor(p, f.getProveedor());
                if (f.getActivo() != null)          setIfActivo(p, f.getActivo());

                if (!dryRun) {
                    p = productoRepository.save(p);
                }

                // 2) Stock inicial idempotente (si aplica)
                if (f.getStockInicial() != null && f.getStockInicial() > 0) {
                    LocalDate fecha = (f.getFechaStockInicial() != null) ? f.getFechaStockInicial() : LocalDate.now();
                    String referencia = "INIT:" + f.getCodigoBarras() + ":" + ISO.format(fecha);

                    boolean existe = existsByReferencia(referencia);
                    if (!existe && !dryRun) {
                        MovimientoInventario cab = new MovimientoInventario();
                        try { cab.getClass().getMethod("setFecha", java.time.LocalDateTime.class)
                                .invoke(cab, fecha.atStartOfDay()); } catch (Exception ignore) {}
                        try { cab.getClass().getMethod("setFecha", java.time.LocalDate.class)
                                .invoke(cab, fecha); } catch (Exception ignore) {}
                        try { cab.getClass().getMethod("setTipo", String.class).invoke(cab, "ENTRADA"); } catch (Exception ignore) {}
                        try { cab.getClass().getMethod("setComentario", String.class).invoke(cab, "Carga inicial"); } catch (Exception ignore) {}
                        try { cab.getClass().getMethod("setReferencia", String.class).invoke(cab, referencia); } catch (Exception ignore) {}
                        cab = movimientoInventarioRepository.save(cab);

                        MovimientoLinea linea = new MovimientoLinea();
                        linea.setMovimiento(cab);
                        linea.setProducto(p);
                        linea.setCantidad(f.getStockInicial());
                        movimientoLineaRepository.save(linea);
                    }
                }

                ok++;
            } catch (Exception ex) {
                skip++;
                errors.add("Fila " + (i + 2) + ": " + ex.getMessage());
                log.warn("Fila {} error: {}", i + 2, ex.getMessage());
            }
        }

        return Result.builder()
                .dryRun(dryRun)
                .totalRows(total)
                .persistedRows(ok)
                .skippedRows(skip)
                .errors(errors)
                .build();
    }

    /* ===================== Validaciones mínimas ===================== */
    private void validarFila(FilaProducto f) {
        if (!nonBlank(f.getCodigoBarras()))
            throw new IllegalArgumentException("codigo_barras vacío");
        if (!nonBlank(f.getNombreProducto()))
            throw new IllegalArgumentException("nombre_producto vacío");
        if (f.getStockInicial() != null && f.getStockInicial() < 0)
            throw new IllegalArgumentException("stock_inicial no puede ser negativo");
        if (f.getFechaStockInicial() != null && f.getFechaStockInicial().isAfter(LocalDate.now().plusYears(50)))
            throw new IllegalArgumentException("fecha_stock_inicial inválida");
    }

    /* ===================== Parsers (SOLO CSV) ===================== */

    // Detecta si es MOVIMIENTOS (fecha, prod_id, prod_name, tipo, cantidad) o PRODUCTOS estándar
    private List<FilaProducto> parseCsvSmart(InputStream in) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) throw new IllegalArgumentException("CSV vacío");
            String[] cols = header.split(",", -1);
            String[] norm = normalize(cols);

            // ¿Archivo de MOVIMIENTOS?
            if (hasAll(norm, "fecha", "prod_id", "prod_name", "tipo", "cantidad")) {
                List<String[]> rows = new ArrayList<>();
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    rows.add(line.split(",", -1));
                }
                return foldMovimientosToProductos(norm, rows);
            }

            // Archivo de PRODUCTOS
            List<FilaProducto> list = new ArrayList<>();
            Map<String, Integer> idx = indexHeaders(norm);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] c = line.split(",", -1);
                list.add(parseRowArray(c, idx));
            }
            return list;
        }
    }

    /* ===================== MOVIMIENTOS → PRODUCTOS ===================== */

    /**
     * Convierte filas de movimientos (fecha, prod_id, prod_name, tipo[I/S], cantidad)
     * a FilaProducto agregada por prod_id.
     * - stock_inicial = max(0, sum(entradas) - sum(salidas))
     * - fecha_stock_inicial = mínima fecha observada
     * - nombre = primer prod_name observado (si no hay, null)
     */
    private List<FilaProducto> foldMovimientosToProductos(String[] headersNorm, List<String[]> rows) {
        int iFecha = indexOf(headersNorm, "fecha");
        int iId    = indexOf(headersNorm, "prod_id");
        int iName  = indexOf(headersNorm, "prod_name");
        int iTipo  = indexOf(headersNorm, "tipo");
        int iCant  = indexOf(headersNorm, "cantidad");

        Map<String, Integer> netoPorProducto = new HashMap<>();
        Map<String, LocalDate> minFechaPorProducto = new HashMap<>();
        Map<String, String> nombrePorProducto = new HashMap<>();

        for (String[] r : rows) {
            String id = safeGet(r, iId);
            if (isBlank(id)) continue;

            String tipo = safeGet(r, iTipo);
            int sign = ("I".equalsIgnoreCase(tipo) || "E".equalsIgnoreCase(tipo) || "ENTRADA".equalsIgnoreCase(tipo)) ? 1 : -1;

            Integer cant = parseInt(safeGet(r, iCant));
            if (cant == null) continue;

            LocalDate fecha = parseDate(safeGet(r, iFecha));
            if (fecha == null) fecha = LocalDate.now();

            String name = safeGet(r, iName);

            netoPorProducto.merge(id, sign * cant, Integer::sum);
            minFechaPorProducto.merge(id, fecha, (a, b) -> a.isBefore(b) ? a : b);
            if (nonBlank(name)) nombrePorProducto.putIfAbsent(id, name);
        }

        List<FilaProducto> out = new ArrayList<>();
        for (String id : netoPorProducto.keySet()) {
            int neto = netoPorProducto.getOrDefault(id, 0);
            Integer stockInicial = Math.max(0, neto); // no permitir negativo
            out.add(FilaProducto.builder()
                    .codigoBarras(id)
                    .nombreProducto(emptyToNull(nombrePorProducto.get(id)))
                    .presentacion(null)
                    .categoria(null)
                    .costoUnitario(null)
                    .precioVenta(null)
                    .stockInicial(stockInicial)
                    .fechaStockInicial(minFechaPorProducto.get(id))
                    .proveedor(null)
                    .activo(null)
                    .build());
        }
        return out;
    }

    /* ===================== Helpers ===================== */

    private static final Map<String, List<String>> ALIASES = Map.of(
            "codigo_barras", List.of("codigo_barras", "codigo", "barcode", "ean", "sku"),
            "nombre_producto", List.of("nombre_producto", "producto", "nombre", "descripcion"),
            "presentacion", List.of("presentacion", "formato", "pres"),
            "categoria", List.of("categoria", "rubro", "familia"),
            "costo_unitario", List.of("costo_unitario", "costo", "cost"),
            "precio_venta", List.of("precio_venta", "precio", "price"),
            "stock_inicial", List.of("stock_inicial", "stock", "cantidad_inicial"),
            "fecha_stock_inicial", List.of("fecha_stock_inicial", "fecha", "f_inicial"),
            "proveedor", List.of("proveedor", "supplier"),
            "activo", List.of("activo", "habilitado", "enabled")
    );

    private Map<String, Integer> indexHeaders(String[] normCols) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < normCols.length; i++) {
            String h = normCols[i];
            for (Map.Entry<String, List<String>> e : ALIASES.entrySet()) {
                if (e.getValue().contains(h)) {
                    idx.put(e.getKey(), i);
                }
            }
        }
        return idx;
    }

    private FilaProducto parseRowArray(String[] c, Map<String, Integer> idx) {
        FilaProducto.FilaProductoBuilder b = FilaProducto.builder();
        b.codigoBarras(get(c, idx.get("codigo_barras")));
        b.nombreProducto(get(c, idx.get("nombre_producto")));
        b.presentacion(get(c, idx.get("presentacion")));
        b.categoria(get(c, idx.get("categoria")));
        b.costoUnitario(parseDecimal(get(c, idx.get("costo_unitario"))));
        b.precioVenta(parseDecimal(get(c, idx.get("precio_venta"))));
        b.stockInicial(parseInt(get(c, idx.get("stock_inicial"))));
        b.fechaStockInicial(parseDate(get(c, idx.get("fecha_stock_inicial"))));
        b.proveedor(get(c, idx.get("proveedor")));
        b.activo(parseBool(get(c, idx.get("activo"))));
        return b.build();
    }

    private String[] normalize(String[] cols) {
        String[] out = new String[cols.length];
        for (int i = 0; i < cols.length; i++) {
            String s = cols[i] == null ? "" : cols[i].toLowerCase(Locale.ROOT).trim();
            s = s.replaceAll("[\\s\\.-]+", "_");
            out[i] = s;
        }
        return out;
    }

    private boolean hasAll(String[] headersNorm, String... expected) {
        Set<String> set = new HashSet<>(Arrays.asList(headersNorm));
        for (String e : expected) if (!set.contains(e)) return false;
        return true;
    }

    private int indexOf(String[] arr, String key) {
        for (int i = 0; i < arr.length; i++) if (Objects.equals(arr[i], key)) return i;
        return -1;
    }

    private String get(String[] arr, Integer i) {
        return (i == null || i < 0 || i >= arr.length) ? null : emptyToNull(arr[i]);
    }

    private String safeGet(String[] arr, int idx) {
        return (idx < 0 || idx >= arr.length) ? null : emptyToNull(arr[idx]);
    }

    private String emptyToNull(String s) { return (s == null || s.trim().isEmpty()) ? null : s.trim(); }
    private boolean nonBlank(String s) { return s != null && !s.trim().isEmpty(); }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private BigDecimal parseDecimal(String s) {
        if (!nonBlank(s)) return null;
        String n = s.replace(" ", "").replace(".", "").replace(",", ".");
        try { return new BigDecimal(n); } catch (Exception e) { return null; }
    }

    private Integer parseInt(String s) {
        if (!nonBlank(s)) return null;
        try { return Integer.parseInt(s.replaceAll("[^0-9-]", "")); } catch (Exception e) { return null; }
    }

    private LocalDate parseDate(String s) {
        if (!nonBlank(s)) return null;
        try { return LocalDate.parse(s, ISO); } catch (Exception e) { return null; }
    }

    private Boolean parseBool(String s) {
        if (!nonBlank(s)) return null;
        String v = s.toLowerCase(Locale.ROOT);
        return v.equals("1") || v.equals("true") || v.equals("si") || v.equals("sí") || v.equals("yes");
    }

    private void setIfPresentacion(Producto p, String val) {
        try { p.getClass().getMethod("setPresentacion", String.class).invoke(p, val); } catch (Exception ignore) {}
    }
    private void setIfCategoria(Producto p, String val) {
        try { p.getClass().getMethod("setCategoria", String.class).invoke(p, val); } catch (Exception ignore) {}
    }
    private void setIfCosto(Producto p, BigDecimal val) {
        try { p.getClass().getMethod("setCosto", BigDecimal.class).invoke(p, val); } catch (Exception ignore) {}
    }
    private void setIfPrecio(Producto p, BigDecimal val) {
        try { p.getClass().getMethod("setPrecio", BigDecimal.class).invoke(p, val); } catch (Exception ignore) {}
    }
    private void setIfProveedor(Producto p, String val) {
        try { p.getClass().getMethod("setProveedor", String.class).invoke(p, val); } catch (Exception ignore) {}
    }
    private void setIfActivo(Producto p, Boolean val) {
        try { p.getClass().getMethod("setActivo", Boolean.class).invoke(p, val); } catch (Exception ignore) {}
    }

    private boolean existsByReferencia(String ref) {
        try {
            return (boolean) movimientoInventarioRepository.getClass()
                    .getMethod("existsByReferencia", String.class)
                    .invoke(movimientoInventarioRepository, ref);
        } catch (Exception e) {
            log.warn("MovimientoInventarioRepository#existsByReferencia(String) no definido; no se verifica idempotencia");
            return false;
        }
    }
}
