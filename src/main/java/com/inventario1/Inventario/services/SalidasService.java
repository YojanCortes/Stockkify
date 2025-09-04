package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.MovimientoInventario;
import com.inventario1.Inventario.models.MovimientoLinea;
import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.TipoMovimiento;
import com.inventario1.Inventario.models.RegistrarSalidaRequest;
import com.inventario1.Inventario.models.RegistrarSalidaResponse;
import com.inventario1.Inventario.repos.MovimientoInventarioRepository;
import com.inventario1.Inventario.repos.ProductoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SalidasService {

    private final MovimientoInventarioRepository movimientoRepo;
    private final ProductoRepository productoRepo;

    public SalidasService(MovimientoInventarioRepository movimientoRepo,
                          ProductoRepository productoRepo) {
        this.movimientoRepo = movimientoRepo;
        this.productoRepo = productoRepo;
    }

    /**
     * Registra una SALIDA con múltiples ítems.
     * - Valida cantidades > 0 y existencia de productos por código de barras.
     * - Construye cabecera + líneas (relación a Producto).
     * - Los TRIGGERS en BD descuentan stock y validan "stock insuficiente".
     */
    @Transactional
    public RegistrarSalidaResponse registrarSalida(RegistrarSalidaRequest req) {
        RegistrarSalidaResponse res = new RegistrarSalidaResponse();

        if (req == null || req.items == null || req.items.isEmpty()) {
            res.ok = false;
            res.errores.add("No hay ítems para registrar.");
            return res;
        }

        // Normaliza: suma cantidades por código si vienen repetidos y valida entradas
        Map<String, Integer> cantidadesPorCodigo = req.items.stream()
                .peek(i -> {
                    if (i.cantidad <= 0) {
                        throw new IllegalArgumentException("Cantidad inválida para " + i.codigo);
                    }
                    if (i.codigo == null || i.codigo.isBlank()) {
                        throw new IllegalArgumentException("Código de barras requerido");
                    }
                })
                .collect(Collectors.toMap(
                        it -> it.codigo,
                        it -> it.cantidad,
                        Integer::sum
                ));

        // Cargar productos en bloque por código de barras
        List<Producto> productos = productoRepo.findAllByCodigoBarrasIn(cantidadesPorCodigo.keySet());
        if (productos.size() != cantidadesPorCodigo.size()) {
            Set<String> encontrados = productos.stream()
                    .map(Producto::getCodigoBarras)
                    .collect(Collectors.toSet());
            String faltantes = cantidadesPorCodigo.keySet().stream()
                    .filter(cb -> !encontrados.contains(cb))
                    .collect(Collectors.joining(", "));
            throw new EntityNotFoundException("Productos no encontrados: " + faltantes);
        }
        Map<String, Producto> porCodigo = productos.stream()
                .collect(Collectors.toMap(Producto::getCodigoBarras, p -> p));

        // Cabecera del movimiento (efectivamente final; no se reasigna)
        final MovimientoInventario mov = new MovimientoInventario();
        mov.setTipo(TipoMovimiento.SALIDA);
        mov.setFecha(LocalDateTime.now());
        mov.setComentario(buildComentario(req.motivo, req.referencia, req.usuario));
        mov.setLineas(new ArrayList<>());

        // Líneas: referencia al Producto; el stock lo ajustan TRIGGERS
        cantidadesPorCodigo.forEach((codigo, cant) -> {
            MovimientoLinea ml = new MovimientoLinea();
            ml.setMovimiento(mov);
            ml.setProducto(porCodigo.get(codigo));
            ml.setCantidad(cant);
            mov.getLineas().add(ml);
        });

        try {
            movimientoRepo.save(mov); // cascade: persiste cabecera y líneas
            res.ok = true;
            res.movimientoId = mov.getId();             // IDENTITY: queda seteado en la misma instancia
            res.registrados = mov.getLineas().size();
            return res;
        } catch (DataIntegrityViolationException ex) {
            // Si un trigger lanza SIGNAL '45000' (p.ej. "Stock insuficiente")
            String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
            res.ok = false;
            res.errores.add(msg != null ? msg : "No se pudo registrar la salida.");
            return res;
        }
    }

    // ---------- Helpers ----------

    private String buildComentario(String motivo, String referencia, String usuario) {
        List<String> partes = new ArrayList<>();
        if (motivo != null && !motivo.isBlank()) partes.add("Motivo: " + motivo);
        if (referencia != null && !referencia.isBlank()) partes.add("Ref: " + referencia);
        if (usuario != null && !usuario.isBlank()) partes.add("Usr: " + usuario);
        return String.join(" | ", partes);
    }
}
