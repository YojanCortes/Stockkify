
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

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Importa MOVIMIENTOS reales (no stock inicial agregado).
 * Crea una cabecera y una línea por cada fila del archivo.
 *
 * Encabezados aceptados (alias):
 *  - fecha:  "fecha", "date", "created_at"
 *  - codigo: "prod_id", "codigo_barras", "ean", "sku"
 *  - nombre: "prod_name", "nombre"
 *  - tipo:   "tipo", "movement", (I/E/ENTRADA/SALIDA)
 *  - cantidad: "cantidad", "qty", "quantity"
 *  - comentario?: "comentario", "comment", "note"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkMovimientoLoaderService {

    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movCabRepo;
    private final MovimientoLineaRepository movLinRepo;

    @Data @Builder
    public static class Result {
        private int totalRows;
        private int persistedRows;
        private int skippedRows;
        private boolean dryRun;
        private List<String> errors;
    }

    private static final DateTimeFormatter D_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter D_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd[ [HH][:mm][:ss]]");

    /* ========== Entrada desde Controller ========== */
    @Transactional
    public Result importar(MultipartFile file, boolean dryRun) {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        List<Map<String,String>> rows;
        try (InputStream in = file.getInputStream()) {
            if (name.endsWith(".csv")) {
                rows = parseCsv(in);
            } else if (name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".xlsm")) {
                throw new IllegalArgumentException("Este servidor está en modo CSV. Exporta a .csv o activa Apache POI.");
                // Si habilitas POI, aquí llamarías parseExcel(in)
            } else {
                rows = parseCsv(in); // intento csv por defecto
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer el archivo: " + e.getMessage(), e);
        }

        return persistRows(rows, dryRun);
    }

    /* ========== Persistencia ========== */
    @Transactional
    protected Result persistRows(List<Map<String,String>> rows, boolean dryRun) {
        int total=0, ok=0, skip=0;
        List<String> errors = new ArrayList<>();

        for (int i=0; i<rows.size(); i++) {
            total++;
            Map<String,String> r = rows.get(i);
            try {
                String codigo = pick(r, "prod_id", "codigo_barras", "ean", "sku");
                String tipoRaw = pick(r, "tipo", "movement");
                String fechaRaw = pick(r, "fecha", "date", "created_at");
                String cantRaw = pick(r, "cantidad", "qty", "quantity");
                String nombre = pick(r, "prod_name", "nombre");
                String comentario = pick(r, "comentario", "comment", "note");

                if (isBlank(codigo)) throw new IllegalArgumentException("codigo vacío (prod_id/codigo_barras)");
                if (isBlank(tipoRaw)) throw new IllegalArgumentException("tipo vacío (I/E/ENTRADA/SALIDA)");
                if (isBlank(fechaRaw)) throw new IllegalArgumentException("fecha vacía (yyyy-MM-dd o yyyy-MM-dd HH:mm:ss)");
                if (isBlank(cantRaw))  throw new IllegalArgumentException("cantidad vacía");
                Integer cantidad = parseInt(cantRaw);
                if (cantidad == null || cantidad <= 0) throw new IllegalArgumentException("cantidad inválida");

                String tipo = normalizeTipo(tipoRaw); // ENTRADA / SALIDA
                LocalDateTime fecha = parseDateTime(fechaRaw);
                if (fecha == null) throw new IllegalArgumentException("fecha inválida");

                // upsert Producto por codigo
                Producto p = productoRepository.findByCodigoBarras(codigo)
                        .orElseGet(() -> {
                            Producto np = new Producto();
                            np.setCodigoBarras(codigo);
                            if (nonBlank(nombre)) np.setNombre(nombre);
                            return np;
                        });
                if (nonBlank(nombre)) {
                    try { p.setNombre(nombre); } catch (Exception ignore) {}
                }
                if (!dryRun && p.getId() == null) {
                    p = productoRepository.save(p);
                }

                // referencia idempotente por contenido de la fila
                String ref = "CSV:" + sha1(codigo + "|" + fecha.toString() + "|" + tipo + "|" + cantidad);

                boolean exists = existsByReferencia(ref);
                if (exists) {
                    skip++;
                    continue;
                }

                if (!dryRun) {
                    // cabecera
                    MovimientoInventario cab = new MovimientoInventario();
                    // set fecha (LocalDateTime o LocalDate según tu entidad)
                    try { cab.getClass().getMethod("setFecha", LocalDateTime.class).invoke(cab, fecha); } catch (Exception ignore) {}
                    try { cab.getClass().getMethod("setFecha", java.time.LocalDate.class).invoke(cab, fecha.toLocalDate()); } catch (Exception ignore) {}
                    try { cab.getClass().getMethod("setTipo", String.class).invoke(cab, tipo); } catch (Exception ignore) {}
                    try { cab.getClass().getMethod("setComentario", String.class).invoke(cab, nonBlank(comentario) ? comentario : "Importación CSV"); } catch (Exception ignore) {}
                    try { cab.getClass().getMethod("setReferencia", String.class).invoke(cab, ref); } catch (Exception ignore) {}

                    cab = movCabRepo.save(cab);

                    // línea
                    MovimientoLinea lin = new MovimientoLinea();
                    lin.setMovimiento(cab);
                    lin.setProducto(p);
                    lin.setCantidad(cantidad);
                    movLinRepo.save(lin);
                }

                ok++;
            } catch (Exception ex) {
                skip++;
                errors.add("Fila " + (i + 2) + ": " + ex.getMessage());
                log.warn("Fila {} error: {}", i+2, ex.getMessage());
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

    private boolean existsByReferencia(String ref) {
        try {
            return movCabRepo.existsByReferencia(ref);
        } catch (Exception e) {
            log.warn("existsByReferencia no disponible, no se verifica idempotencia");
            return false;
        }
    }

    /* ========== CSV parsing sencillo con autodetección de separador ========== */
    private List<Map<String,String>> parseCsv(InputStream in) throws Exception {
        List<Map<String,String>> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) throw new IllegalArgumentException("CSV vacío");
            char sep = guessDelimiter(header);
            String[] cols = split(header, sep);
            String[] norm = normalize(cols);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] c = split(line, sep);
                Map<String,String> row = new HashMap<>();
                for (int i=0; i<norm.length && i<c.length; i++) {
                    row.put(norm[i], emptyToNull(c[i]));
                }
                out.add(row);
            }
        }
        return out;
    }

    private char guessDelimiter(String line) {
        int commas = count(line, ',');
        int semis  = count(line, ';');
        int tabs   = count(line, '\t');
        if (semis >= commas && semis >= tabs) return ';';
        if (tabs  >= commas && tabs  >= semis) return '\t';
        return ','; // por defecto coma
    }
    private int count(String s, char ch) { int n=0; for (char c: s.toCharArray()) if (c==ch) n++; return n; }
    private String[] split(String s, char sep) { return s.split(java.util.regex.Pattern.quote(String.valueOf(sep)), -1); }

    /* ========== utilidades ========== */
    private String pick(Map<String,String> r, String... keys) {
        for (String k : keys) {
            String v = r.get(k);
            if (!isBlank(v)) return v;
        }
        return null;
    }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private boolean nonBlank(String s) { return !isBlank(s); }

    private Integer parseInt(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9-]", "")); } catch (Exception e) { return null; }
    }
    private LocalDateTime parseDateTime(String s) {
        try { return LocalDateTime.parse(s, D_DATETIME); } catch (Exception ignore) {}
        try { return LocalDate.parse(s, D_DATE).atStartOfDay(); } catch (Exception ignore) {}
        return null;
    }
    private String emptyToNull(String s) { return isBlank(s) ? null : s.trim(); }

    private String normalizeTipo(String t) {
        String v = t.trim().toUpperCase(Locale.ROOT);
        if (v.equals("I") || v.equals("E") || v.startsWith("ENTR")) return "ENTRADA";
        return "SALIDA";
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

    private String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, hash).toString(16);
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }
}
