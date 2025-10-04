package com.inventario1.Inventario.web;

import com.inventario1.Inventario.services.BulkProductoLoaderService;
import com.inventario1.Inventario.services.SyntheticDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/utils")
@RequiredArgsConstructor
public class DataGenerationController {

    private final SyntheticDataService syntheticDataService;
    private final BulkProductoLoaderService bulkProductoLoaderService;

    /**
     * 1) Genera un CSV descargable con N productos sintéticos.
     *    Compatible con /ui/bulk-productos (carga masiva).
     */
    @GetMapping(value = "/generar/productos.csv", produces = "text/csv")
    public ResponseEntity<ByteArrayResource> generarProductosCsv(
            @RequestParam(defaultValue = "1000") int n,
            @RequestParam(defaultValue = "true") boolean withStock,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(defaultValue = "30") int daysRange,
            @RequestParam(required = false) Long seed
    ) {
        List<BulkProductoLoaderService.FilaProducto> filas =
                syntheticDataService.generarProductos(n, withStock, startDate, daysRange, seed);

        String csv = toCsv(filas);
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
     * 2) Genera y carga directamente (sin descargar) usando tu BulkProductoLoaderService.
     *    Devuelve el resumen JSON (OK, saltados, errores). Usa dryRun para validar sin persistir.
     */
    @PostMapping("/generar-y-cargar/productos")
    public ResponseEntity<BulkProductoLoaderService.Result> generarYCargarProductos(
            @RequestParam(defaultValue = "1000") int n,
            @RequestParam(defaultValue = "true") boolean withStock,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(defaultValue = "30") int daysRange,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(required = false) Long seed
    ) {
        List<BulkProductoLoaderService.FilaProducto> filas =
                syntheticDataService.generarProductos(n, withStock, startDate, daysRange, seed);

        BulkProductoLoaderService.Result result = bulkProductoLoaderService.procesar(filas, dryRun);
        return ResponseEntity.status(result.hasErrors() ? 207 : 200).body(result);
    }

    /* ===== CSV builder compatible con tu loader ===== */
    private String toCsv(List<BulkProductoLoaderService.FilaProducto> filas) {
        StringBuilder sb = new StringBuilder();
        sb.append("codigo_barras,nombre_producto,presentacion,categoria,costo_unitario,precio_venta,stock_inicial,fecha_stock_inicial,proveedor,activo\n");
        for (BulkProductoLoaderService.FilaProducto f : filas) {
            sb.append(esc(f.getCodigoBarras())).append(',');
            sb.append(esc(f.getNombreProducto())).append(',');
            sb.append(esc(f.getPresentacion())).append(',');
            sb.append(esc(f.getCategoria())).append(',');
            sb.append(num(f.getCostoUnitario())).append(',');
            sb.append(num(f.getPrecioVenta())).append(',');
            sb.append(intOrEmpty(f.getStockInicial())).append(',');
            sb.append(dateOrEmpty(f.getFechaStockInicial())).append(',');
            sb.append(esc(f.getProveedor())).append(',');
            sb.append(boolOrEmpty(f.getActivo())).append('\n');
        }
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        String v = s.replace("\"","\"\"");
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v + "\"";
        }
        return v;
    }
    private String num(java.math.BigDecimal n) { return n == null ? "" : n.toPlainString(); }
    private String intOrEmpty(Integer n) { return n == null ? "" : String.valueOf(n); }
    private String dateOrEmpty(LocalDate d) { return d == null ? "" : d.toString(); }
    private String boolOrEmpty(Boolean b) { return b == null ? "" : (b ? "true" : "false"); }
}
