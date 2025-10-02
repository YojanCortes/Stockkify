package com.inventario1.Inventario.services.dto;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Carga masiva de Productos (usa directamente tu entidad models.Producto).
 * - Valida duplicados por código de barras dentro del lote
 * - Normaliza campos básicos
 * - Soporta dryRun (simulación) y upsert (crear o actualizar)
 * - Procesa en bloques (chunkSize)
 */
@Service
public class BulkProductoService {

    private final ProductoRepository productoRepository;

    public BulkProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    /** Validaciones de negocio e idempotencia básica en memoria (duplicados en el lote) */
    public List<String> validateItems(List<Producto> items) {
        List<String> global = new ArrayList<>();

        // 1) Duplicados dentro del lote por código de barras (tras normalizar a "safe")
        Map<String, Long> counts = items.stream()
                .collect(Collectors.groupingBy(p -> safe(cb(p)), Collectors.counting()));
        counts.forEach((cb, n) -> {
            if (cb != null && n > 1) global.add("Código repetido en el lote: " + cb);
        });

        // 2) (Opcional) Reglas adicionales aquí (rangos, combos, etc.)
        return global;
    }

    private String cb(Producto p) {
        return (p == null) ? null : p.getCodigoBarras();
    }

    private String safe(String s) {
        return (s == null) ? null : s.trim();
    }

    /** Normaliza valores para reducir rechazos (trim, dígitos en CB, etc.) */
    public void normalize(Producto p) {
        if (p == null) return;
        if (p.getNombre() != null) p.setNombre(p.getNombre().trim());
        if (p.getMarca()  != null) p.setMarca(p.getMarca().trim());
        if (p.getCodigoBarras() != null) {
            p.setCodigoBarras(p.getCodigoBarras().replaceAll("\\D", "").trim());
        }
        // Agrega más reglas si necesitas (capitalización, límites, defaults, etc.)
    }

    /** Inserta/actualiza por bloques; si upsert=true actualiza si ya existe por código de barras */
    @Transactional
    public BulkResult process(List<Producto> items, boolean dryRun, boolean upsert, int chunkSize) {
        BulkResult result = new BulkResult();

        if (items == null || items.isEmpty()) return result;

        // Pre-normaliza todos
        for (Producto it : items) normalize(it);

        // Pre-carga existencia por CB en un solo hit (método repositorio requerido)
        List<String> cbs = items.stream().map(this::cb).filter(Objects::nonNull).toList();
        Set<String> existentes = new HashSet<>(productoRepository.findExistingCodigos(cbs));

        // Procesa por bloques
        for (int i = 0; i < items.size(); i += Math.max(1, chunkSize)) {
            List<Producto> slice = items.subList(i, Math.min(i + Math.max(1, chunkSize), items.size()));
            if (dryRun) {
                // Simulación: no guarda, solo valida/contabiliza potenciales acciones
                for (int k = 0; k < slice.size(); k++) {
                    Producto it = slice.get(k);
                    boolean yaExiste = it.getCodigoBarras() != null && existentes.contains(it.getCodigoBarras());
                    if (yaExiste && !upsert) {
                        result.incSkipped();
                        result.addError(i + k, it.getCodigoBarras(), "Ya existe (simulado). Activa 'upsert' para actualizar.");
                    } else if (yaExiste) {
                        result.incUpdated(); // simulado
                    } else {
                        result.incInserted(); // simulado
                    }
                }
                continue;
            }

            // Persistencia real
            for (int k = 0; k < slice.size(); k++) {
                Producto it = slice.get(k);
                try {
                    String codigo = it.getCodigoBarras();
                    boolean yaExiste = codigo != null && existentes.contains(codigo);

                    if (yaExiste && !upsert) {
                        result.incSkipped();
                        result.addError(i + k, codigo, "Ya existe (saltado). Activa 'upsert' para actualizar.");
                        continue;
                    }

                    Producto entity;
                    if (yaExiste) {
                        entity = productoRepository.findByCodigoBarras(codigo).orElseGet(Producto::new);
                    } else {
                        entity = new Producto();
                    }

                    // Mapear campos (como usas la misma entidad, es copia directa)
                    entity.setNombre(it.getNombre());
                    entity.setMarca(it.getMarca());
                    entity.setCategoria(it.getCategoria());
                    entity.setUnidadBase(it.getUnidadBase());
                    entity.setVolumenNominalMl(it.getVolumenNominalMl());
                    entity.setGraduacionAlcoholica(it.getGraduacionAlcoholica());
                    entity.setFechaVencimiento(it.getFechaVencimiento());
                    entity.setStockActual(it.getStockActual());
                    entity.setStockMinimo(it.getStockMinimo());
                    entity.setCodigoBarras(codigo);
                    // después (compatible con getters getXxx()):
                    entity.setPerecible( it.getPerecible()  != null ? it.getPerecible()  : false);
                    entity.setRetornable(it.getRetornable() != null ? it.getRetornable() : false);
                    entity.setActivo(    it.getActivo()     != null ? it.getActivo()     : true);

                    productoRepository.save(entity);

                    if (yaExiste) result.incUpdated(); else result.incInserted();

                } catch (Exception ex) {
                    result.addError(i + k, it.getCodigoBarras(), "Error al guardar: " + ex.getMessage());
                }
            }
        }
        return result;
    }

    // ===== Resultado de la operación (embebido para evitar dependencias externas) =====
    public static class BulkResult {
        public static class ItemError {
            public final int index;
            public final String codigoBarras;
            public final String mensaje;
            public ItemError(int index, String cb, String msg) {
                this.index = index; this.codigoBarras = cb; this.mensaje = msg;
            }
        }
        private int inserted;
        private int updated;
        private int skipped;
        private final List<ItemError> errors = new ArrayList<>();

        public void incInserted() { inserted++; }
        public void incUpdated()  { updated++; }
        public void incSkipped()  { skipped++; }
        public void addError(int idx, String cb, String msg) { errors.add(new ItemError(idx, cb, msg)); }

        public int getInserted() { return inserted; }
        public int getUpdated()  { return updated; }
        public int getSkipped()  { return skipped; }
        public List<ItemError> getErrors() { return errors; }
    }
}
