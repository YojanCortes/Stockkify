// path: src/main/java/com/inventario1/Inventario/web/DataGenerationController.java
package com.inventario1.Inventario.web;

import com.inventario1.Inventario.services.BulkProductoLoaderService;
import com.inventario1.Inventario.services.SyntheticDataService;
import com.inventario1.Inventario.services.dto.FilaProducto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Utilidades para generar datos y CSV compatibles con /bulk-productos.
 */
@RestController
@RequestMapping("/utils")
@RequiredArgsConstructor
public class DataGenerationController {

    private final SyntheticDataService syntheticDataService;
    private final BulkProductoLoaderService bulkProductoLoaderService;

    /**
     * 1) Descarga CSV de N productos sintéticos (compatible con /bulk-productos).
     */
    @GetMapping(value = "/generar/productos.csv", produces = "text/csv")
    public ResponseEntity<ByteArrayResource> generarProductosCsv(
            @RequestParam(defaultValue = "1000") int n,
            @RequestParam(defaultValue = "true") boolean withStock,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(defaultValue = "30") int daysRange,
            @RequestParam(required = false) Long seed
    ) {
        List<FilaProducto> filas = syntheticDataService.generarProductos(n, withStock, startDate, daysRange, seed);

        String csv = toCsvProductos(filas);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource res = new ByteArrayResource(bytes);

        String fname = "productos_sinteticos_" + n + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fname + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(bytes.length)
                .body(res);
    }

    /**
     * 2) Genera y carga directamente al importador (evita descargar).
     *    Usa dryRun=true para validar sin persistir.
     */
    @PostMapping("/generar-y-cargar/productos")
    public ResponseEntity<BulkProductoLoaderService.ImportResult> generarYCargarProductos(
            @RequestParam(defaultValue = "1000") int n,
            @RequestParam(defaultValue = "true") boolean withStock,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(defaultValue = "30") int daysRange,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(required = false) Long seed
    ) throws Exception {
        List<FilaProducto> filas = syntheticDataService.generarProductos(n, withStock, startDate, daysRange, seed);
        String csv = toCsvProductos(filas);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        MultipartFile memFile = new InMemoryMultipartFile(
                "file",
                "productos_sinteticos.csv",
                "text/csv; charset=UTF-8",
                bytes
        );
        BulkProductoLoaderService.ImportResult result = bulkProductoLoaderService.importar(memFile, dryRun);
        return ResponseEntity.status((result.getErrors() != null && !result.getErrors().isEmpty()) ? 207 : 200)
                .body(result);
    }

    /* ===== Helpers ===== */

    // CSV compatible con BulkProductoLoaderService: encabezados canónicos
    private String toCsvProductos(List<FilaProducto> filas) {
        StringBuilder sb = new StringBuilder();
        sb.append("codigo_barras,nombre,marca,precio,categoria,unidad_base,volumen_nominal_ml,graduacion_alcoholica,perecible,retornable,stock_minimo,activo\n");
        for (FilaProducto f : filas) {
            sb.append(esc(f.getCodigoBarras())).append(',');
            sb.append(esc(f.getNombre())).append(',');
            sb.append(esc(f.getMarca())).append(',');
            sb.append(intOrEmpty(f.getPrecio())).append(',');
            sb.append(esc(f.getCategoria() != null ? f.getCategoria().name() : null)).append(',');
            sb.append(esc(f.getUnidadBase() != null ? f.getUnidadBase().name() : null)).append(',');
            sb.append(intOrEmpty(f.getVolumenNominalMl())).append(',');
            sb.append(doubleOrEmpty(f.getGraduacionAlcoholica())).append(',');
            sb.append(boolOrEmpty(f.getPerecible())).append(',');
            sb.append(boolOrEmpty(f.getRetornable())).append(',');
            sb.append(intOrEmpty(f.getStockMinimo())).append(',');
            sb.append(boolOrEmpty(f.getActivo())).append('\n');
        }
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return (v.contains(",") || v.contains("\"") || v.contains("\n")) ? ("\"" + v + "\"") : v;
    }
    private String intOrEmpty(Integer n) { return n == null ? "" : String.valueOf(n); }
    private String doubleOrEmpty(Double n) { return n == null ? "" : String.valueOf(n); }
    private String boolOrEmpty(Boolean b) { return b == null ? "" : (b ? "1" : "0"); }

    /**
     * MultipartFile en memoria sin dependencias externas.
     */
    static final class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content != null ? content : new byte[0];
        }
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public ByteArrayInputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException { throw new UnsupportedOperationException("transferTo no soportado"); }
    }
}
