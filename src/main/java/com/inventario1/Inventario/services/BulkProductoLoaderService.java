// path: src/main/java/com/inventario1/Inventario/services/BulkProductoLoaderService.java
package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import com.inventario1.Inventario.services.dto.FilaProducto;
import com.inventario1.Inventario.services.mapper.CsvProductoMapper;
import com.inventario1.Inventario.util.csv.FlexibleCsvReader;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkProductoLoaderService {

    private final ProductoRepository productoRepository;

    @Data @Builder
    public static class ImportResult {
        private int totalRows;
        private int persistedRows;
        private int skippedRows;
        private boolean dryRun;
        private List<String> errors;
        private List<String> warnings;
        private List<TableSummary> tables;
    }

    @Data @Builder
    public static class TableSummary {
        private String tabla;
        private Integer insertados;
        private Integer actualizados;
        private Integer saltados;
    }

    public ImportResult importar(MultipartFile file, boolean dryRun) throws Exception {
        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final AtomicInteger total = new AtomicInteger(0);
        final AtomicInteger persisted = new AtomicInteger(0);
        final AtomicInteger skipped = new AtomicInteger(0);
        final AtomicInteger inserted = new AtomicInteger(0);
        final AtomicInteger updated = new AtomicInteger(0);

        final FlexibleCsvReader reader = new FlexibleCsvReader();

        // Alias (incluye 'codigo_barra')
        reader.addHeaderAlias("codigo de barras","codigo_barras");
        reader.addHeaderAlias("código de barras","codigo_barras");
        reader.addHeaderAlias("cod_barras","codigo_barras");
        reader.addHeaderAlias("codigo barra","codigo_barras");
        reader.addHeaderAlias("codigo_barra","codigo_barras");
        reader.addHeaderAlias("precio venta","precio");
        reader.addHeaderAlias("categoría","categoria");
        reader.addHeaderAlias("unidad base","unidad_base");
        reader.addHeaderAlias("volumen","volumen_nominal_ml");
        reader.addHeaderAlias("volumen_ml","volumen_nominal_ml");
        reader.addHeaderAlias("graduacion","graduacion_alcoholica");
        reader.addHeaderAlias("stock minimo","stock_minimo");
        reader.addHeaderAlias("activo?","activo");
        reader.addHeaderAlias("perecible?","perecible");
        reader.addHeaderAlias("retornable?","retornable");

        // Requeridos (PRODUCTOS)
        reader.setRequiredHeaders(List.of("codigo_barras","nombre","marca","precio","categoria","unidad_base"));

        // Pase 1: valida encabezados y detecta CSV de movimientos para mensaje claro
        try (InputStream in = file.getInputStream()) {
            reader.read(in, r -> {});
        } catch (Exception e) {
            List<String> hdrs = reader.getLastHeaders();
            if (!hdrs.isEmpty()) {
                if (hdrs.containsAll(Arrays.asList("codigo_barras","precio","fecha","tipo","cantidad"))
                        || hdrs.containsAll(Arrays.asList("codigo_barra","precio","fecha","tipo","cantidad"))) {
                    throw new IllegalArgumentException(
                            "El archivo parece de MOVIMIENTOS (codigo_barra, precio, fecha, tipo, cantidad). Usa /bulk-movimientos."
                    );
                }
            }
            throw e;
        }

        // Pase 2: procesar filas
        try (InputStream in = file.getInputStream()) {
            reader.read(in, row -> {
                if (row.lineNumber == 1) return;
                total.incrementAndGet();

                FilaProducto f = CsvProductoMapper.fromRow(row);
                if (!f.isValid()) {
                    skipped.incrementAndGet();
                    errors.add("Fila " + row.lineNumber + ": " + f.getError());
                    return;
                }

                try {
                    if (!dryRun) {
                        Producto p = productoRepository.findByCodigoBarras(f.getCodigoBarras()).orElse(null);
                        boolean isNew = (p == null);
                        if (isNew) p = new Producto();

                        p.setCodigoBarras(f.getCodigoBarras());
                        p.setNombre(f.getNombre());
                        p.setMarca(f.getMarca());
                        p.setPrecio(f.getPrecio()); // tu entidad es int
                        p.setCategoria(f.getCategoria());
                        p.setUnidadBase(f.getUnidadBase());
                        p.setVolumenNominalMl(f.getVolumenNominalMl());
                        p.setGraduacionAlcoholica(f.getGraduacionAlcoholica());
                        p.setPerecible(f.getPerecible() != null ? f.getPerecible() : Boolean.FALSE);
                        p.setRetornable(f.getRetornable() != null ? f.getRetornable() : Boolean.FALSE);
                        if (f.getStockMinimo() != null) p.setStockMinimo(f.getStockMinimo());
                        p.setActivo(f.getActivo() == null ? Boolean.TRUE : f.getActivo());

                        if (isNew) p.setCreadoEn(LocalDateTime.now());
                        p.setActualizadoEn(LocalDateTime.now());

                        productoRepository.save(p);
                        persisted.incrementAndGet();
                        if (isNew) inserted.incrementAndGet(); else updated.incrementAndGet();
                    } else {
                        persisted.incrementAndGet();
                    }
                } catch (Exception ex) {
                    skipped.incrementAndGet();
                    errors.add("Fila " + row.lineNumber + ": error al persistir [" + ex.getMessage() + "]");
                }
            });
        }

        warnings.addAll(reader.getWarnings());

        final List<TableSummary> tables = List.of(
                TableSummary.builder()
                        .tabla("productos")
                        .insertados(inserted.get())
                        .actualizados(updated.get())
                        .saltados(skipped.get())
                        .build()
        );

        return ImportResult.builder()
                .totalRows(total.get())
                .persistedRows(persisted.get())
                .skippedRows(skipped.get())
                .dryRun(dryRun)
                .errors(errors)
                .warnings(warnings)
                .tables(tables)
                .build();
    }
}
