// path: src/main/java/com/inventario1/Inventario/services/BulkMovimientoLoaderService.java
package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.MovimientoInventario;
import com.inventario1.Inventario.models.MovimientoLinea;
import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.TipoMovimiento;
import com.inventario1.Inventario.models.Categoria;
import com.inventario1.Inventario.models.UnidadBase;
import com.inventario1.Inventario.repos.MovimientoInventarioRepository;
import com.inventario1.Inventario.repos.MovimientoLineaRepository;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkMovimientoLoaderService {

    final ProductoRepository productoRepository;
    final MovimientoInventarioRepository movCabRepo;
    final MovimientoLineaRepository movLinRepo;

    @Data @Builder
    public static class Result {
        int totalRows;
        int persistedRows;
        int skippedRows;
        boolean dryRun;
        List<String> errors;
        List<String> warnings;
        List<TableSummary> tables;
    }

    @Data @Builder
    public static class TableSummary {
        String tabla;
        Integer insertados;
        Integer actualizados;
        Integer saltados;
    }

    static final DateTimeFormatter D_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static final DateTimeFormatter D_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd['T'][ ]HH[:mm[:ss]]");

    @Transactional
    public Result importar(MultipartFile file, boolean dryRun) {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        List<Map<String,String>> rows;
        try (InputStream in = file.getInputStream()) {
            if (name.endsWith(".csv")) rows = parseCsv(in);
            else if (name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".xlsm")) rows = parseExcel(in);
            else rows = parseCsv(in);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer el archivo: " + e.getMessage(), e);
        }
        return persistRows(rows, dryRun);
    }

    @Transactional
    Result persistRows(List<Map<String,String>> rows, boolean dryRun) {
        int total=0, ok=0, skip=0;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i=0; i<rows.size(); i++) {
            total++;
            Map<String,String> r = rows.get(i);
            try {
                String rawCodigo = pick(r, "prod_id","codigo_barras","codigo_barra","ean","sku","codigo");
                String tipoRaw   = pick(r, "tipo","movement","operacion");
                String fechaRaw  = pick(r, "fecha","date","created_at","fecha_mov","fecha_operacion");
                String cantRaw   = pick(r, "cantidad","qty","quantity","cantidad_unidades");
                String comentario= pick(r, "comentario","comment","note");

                String codigo = normalizeBarcode(rawCodigo);
                if (isBlank(codigo)) throw new IllegalArgumentException("codigo vacío");

                TipoMovimiento tipo = normalizeTipo(tipoRaw);
                if (tipo == null) throw new IllegalArgumentException("tipo inválido (usa I/S o ENTRADA/SALIDA)");

                LocalDateTime fecha = parseDateTimeDefault(fechaRaw, now);
                int cantidad = parseIntSafe(cantRaw, 1);
                if (cantidad <= 0) throw new IllegalArgumentException("cantidad inválida");

                Producto p = productoRepository.findByCodigoBarras(codigo).orElse(null);
                if (p == null) {
                    if (!dryRun) {
                        p = new Producto();
                        p.setCodigoBarras(codigo);
                        p.setNombre("Producto " + codigo);
                        p.setMarca("N/A");
                        p.setPrecio(0);
                        p.setCategoria(Optional.ofNullable(parseCategoria(pick(r,"categoria"))).orElse(Categoria.GENERAL));
                        p.setUnidadBase(Optional.ofNullable(parseUnidadBase(pick(r,"unidad_base"))).orElse(UnidadBase.UNIDAD));
                        p.setVolumenNominalMl(0);
                        p.setGraduacionAlcoholica(0.0);
                        p.setPerecible(false);
                        p.setRetornable(false);
                        p.setStockActual(0);
                        p.setStockMinimo(0);
                        p.setActivo(true);
                        p.setCreadoEn(now);
                        p.setActualizadoEn(now);
                        p.setVersion(1L);
                        p = productoRepository.save(p);
                    } else {
                        p = new Producto();
                        p.setCodigoBarras(codigo);
                    }
                }

                String ref = "CSV:" + sha1(codigo + "|" + fecha + "|" + tipo + "|" + cantidad);
                if (existsByReferencia(ref)) { skip++; continue; }

                if (!dryRun) {
                    MovimientoInventario cab = new MovimientoInventario();
                    cab.setFecha(fecha);
                    cab.setTipo(tipo); // I = ENTRADA, S = SALIDA
                    cab.setComentario(nonBlank(comentario) ? comentario : "Importación");
                    cab.setReferencia(ref);
                    cab = movCabRepo.save(cab);

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

        List<TableSummary> tables = List.of(
                TableSummary.builder().tabla("movimientos_inventario").insertados(ok).actualizados(0).saltados(skip).build(),
                TableSummary.builder().tabla("movimiento_lineas").insertados(ok).actualizados(0).saltados(skip).build()
        );

        return Result.builder()
                .dryRun(dryRun)
                .totalRows(total)
                .persistedRows(ok)
                .skippedRows(skip)
                .errors(errors)
                .warnings(warnings)
                .tables(tables)
                .build();
    }

    boolean existsByReferencia(String ref) {
        try { return movCabRepo.existsByReferencia(ref); }
        catch (Exception e) { log.warn("existsByReferencia no disponible"); return false; }
    }

    List<Map<String,String>> parseCsv(InputStream in) throws Exception {
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
                Map<String,String> row = new LinkedHashMap<>();
                for (int i=0; i<norm.length; i++) {
                    String v = i<c.length ? c[i] : "";
                    row.put(norm[i], emptyToNull(v));
                }
                out.add(row);
            }
        }
        return out;
    }

    List<Map<String,String>> parseExcel(InputStream in) throws IOException, InvalidFormatException {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sh = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sh == null) return List.of();

            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();

            Row h = sh.getRow(sh.getFirstRowNum());
            if (h == null) return List.of();

            int lastCol = h.getLastCellNum();
            List<String> headers = new ArrayList<>(lastCol);
            for (int c = 0; c < lastCol; c++) {
                Cell cell = h.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String raw = cell == null ? "" : fmt.formatCellValue(cell, eval);
                headers.add(norm(raw));
            }

            List<Map<String,String>> out = new ArrayList<>();
            for (int r = sh.getFirstRowNum() + 1; r <= sh.getLastRowNum(); r++) {
                Row row = sh.getRow(r);
                if (row == null) continue;
                Map<String,String> map = new LinkedHashMap<>();
                boolean allBlank = true;
                for (int c = 0; c < headers.size(); c++) {
                    String key = headers.get(c);
                    if (key.isBlank()) continue;
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String v = "";
                    if (cell != null) {
                        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                            var ldt = cell.getLocalDateTimeCellValue();
                            v = (ldt.getHour()==0 && ldt.getMinute()==0 && ldt.getSecond()==0)
                                    ? ldt.toLocalDate().toString()
                                    : ldt.toString().replace('T',' ');
                        } else {
                            v = fmt.formatCellValue(cell, eval).trim();
                        }
                    }
                    if (!v.isBlank()) allBlank = false;
                    map.put(key, emptyToNull(v));
                }
                if (!allBlank) out.add(map);
            }
            return out;
        }
    }

    // --- utils de parseo/normalización ---

    char guessDelimiter(String line) {
        int commas = count(line, ','), semis = count(line, ';'), tabs = count(line, '\t');
        if (semis >= commas && semis >= tabs) return ';';
        if (tabs >= commas && tabs >= semis) return '\t';
        return ','; // por qué: CSV estándar
    }
    int count(String s, char ch) { int n=0; for (char c: s.toCharArray()) if (c==ch) n++; return n; }
    String[] split(String s, char sep) { return s.split(java.util.regex.Pattern.quote(String.valueOf(sep)), -1); }

    String pick(Map<String,String> r, String... keys) {
        for (String k : keys) { String v = r.get(k); if (!isBlank(v)) return v; }
        return null;
    }
    boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    boolean nonBlank(String s) { return !isBlank(s); }
    String emptyToNull(String s) { return isBlank(s) ? null : s.trim(); }

    Integer parseInt(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9-]", "")); } catch (Exception e) { return null; }
    }
    Integer parseIntSafe(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s.replaceAll("[^0-9-]", "")); } catch (Exception e) { return def; }
    }

    LocalDateTime parseDateTime(String s) {
        try { return LocalDateTime.parse(s.trim(), D_DATETIME); } catch (Exception ignore) {}
        try { return LocalDate.parse(s.trim(), D_DATE).atStartOfDay(); } catch (Exception ignore) {}
        return null;
    }
    LocalDateTime parseDateTimeDefault(String s, LocalDateTime def) {
        if (s == null || s.isBlank()) return def;
        LocalDateTime dt = parseDateTime(s);
        return dt != null ? dt : def;
    }

    /** I/ENTRADA/IN -> ENTRADA; S/SALIDA/OUT -> SALIDA; AJUSTE permitido; null si inválido. */
    TipoMovimiento normalizeTipo(String t) {
        if (t == null) return null;
        String v = t.trim().toUpperCase(Locale.ROOT)
                .replace("Ó","O").replace("Á","A").replace("É","E").replace("Í","I").replace("Ú","U").replace("Ñ","N")
                .replaceAll("\\s+","");
        switch (v) {
            case "I": case "IN": case "ENTRADA": return TipoMovimiento.ENTRADA;
            case "S": case "OUT": case "SALIDA": return TipoMovimiento.SALIDA;
            case "AJUSTE": case "AJU": return TipoMovimiento.AJUSTE; // si existe en tu enum
            default: return null; // por qué: no adivinar
        }
    }

    String[] normalize(String[] cols) {
        String[] out = new String[cols.length];
        for (int i = 0; i < cols.length; i++) out[i] = norm(cols[i]);
        return out;
    }
    String norm(String s) {
        s = s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
        s = s.replaceAll("[\\s\\.-]+", "_")
                .replace("ó","o").replace("á","a").replace("é","e").replace("í","i").replace("ú","u").replace("ñ","n");
        return s;
    }

    Categoria parseCategoria(String s) {
        if (s == null || s.isBlank()) return Categoria.GENERAL;
        String k = s.trim().toUpperCase(Locale.ROOT)
                .replace("Ó","O").replace("Á","A").replace("É","E").replace("Í","I").replace("Ú","U").replace("Ñ","N")
                .replaceAll("[\\s\\-]+","_");
        try { return Categoria.valueOf(k); } catch (Exception e) { return Categoria.GENERAL; }
    }
    UnidadBase parseUnidadBase(String s) {
        if (s == null || s.isBlank()) return UnidadBase.UNIDAD;
        String k = s.trim().toUpperCase(Locale.ROOT)
                .replace("Ó","O").replace("Á","A").replace("É","E").replace("Í","I").replace("Ú","U").replace("Ñ","N")
                .replaceAll("[\\s\\-]+","_");
        try { return UnidadBase.valueOf(k); } catch (Exception e) { return UnidadBase.UNIDAD; }
    }

    String normalizeBarcode(String v) {
        if (v == null) return null;
        String s = v.trim().replace(",", ".");
        try { if (s.toLowerCase(Locale.ROOT).contains("e")) s = String.valueOf((long)Double.parseDouble(s)); } catch (Exception ignored) {}
        if (s.endsWith(".0")) s = s.substring(0, s.length()-2);
        String digits = s.replaceAll("\\D+", "");
        return digits.isEmpty() ? null : digits;
    }

    String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, hash).toString(16);
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }
}
