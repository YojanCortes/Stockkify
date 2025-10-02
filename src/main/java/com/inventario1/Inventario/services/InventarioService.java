package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.MovimientoInventario;
import com.inventario1.Inventario.models.MovimientoLinea;
import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.TipoMovimiento;
import com.inventario1.Inventario.repos.MovimientoInventarioRepository;
import com.inventario1.Inventario.repos.ProductoRepository;
import com.inventario1.Inventario.services.dto.AlertaDTO;
import com.inventario1.Inventario.services.dto.LineaMovimientoInput;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventarioService {

    private final ProductoRepository productoRepo;
    private final MovimientoInventarioRepository movRepo;

    // =========================
    //   PRODUCTOS
    // =========================
    @Transactional(readOnly = true)
    public Page<Producto> listarProductos(Pageable pageable) {
        return productoRepo.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Producto> buscarProductos(String q, Pageable pageable) {
        if (q == null || q.isBlank()) return listarProductos(pageable);
        return productoRepo.search(q.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Producto leerProducto(String codigoBarras) {
        return productoRepo.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + codigoBarras));
    }

    // =========================
    //   REGISTRO DE MOVIMIENTOS
    // =========================
    @Transactional
    public MovimientoInventario registrarSalida(String codigoBarras, int cantidad, String comentario) {
        return registrarSalida(List.of(new LineaMovimientoInput(codigoBarras, cantidad)), comentario);
    }

    @Transactional
    public MovimientoInventario registrarEntrada(String codigoBarras, int cantidad, String comentario) {
        return registrarEntrada(List.of(new LineaMovimientoInput(codigoBarras, cantidad)), comentario);
    }

    @Transactional
    public MovimientoInventario registrarSalida(List<LineaMovimientoInput> items, String comentario) {
        return registrarMovimiento(TipoMovimiento.SALIDA, items, comentario);
    }

    @Transactional
    public MovimientoInventario registrarEntrada(List<LineaMovimientoInput> items, String comentario) {
        return registrarMovimiento(TipoMovimiento.ENTRADA, items, comentario);
    }

    @Transactional
    public MovimientoInventario registrarAjuste(List<LineaMovimientoInput> items, String comentario) {
        return registrarMovimiento(TipoMovimiento.AJUSTE, items, comentario);
    }

    private MovimientoInventario registrarMovimiento(TipoMovimiento tipo,
                                                     List<LineaMovimientoInput> items,
                                                     String comentario) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Debe indicar al menos una línea.");
        }

        Map<String, Integer> cantidadesPorCodigo = items.stream()
                .peek(i -> {
                    if (i.cantidad() <= 0) throw new IllegalArgumentException("Cantidad debe ser > 0");
                    if (i.codigoBarras() == null || i.codigoBarras().isBlank())
                        throw new IllegalArgumentException("Código de barras requerido");
                })
                .collect(Collectors.toMap(
                        LineaMovimientoInput::codigoBarras,
                        LineaMovimientoInput::cantidad,
                        Integer::sum
                ));

        // Cargar productos en bloque por código de barras
        List<Producto> productos = productoRepo.findAllByCodigoBarrasIn(cantidadesPorCodigo.keySet());
        if (productos.size() != cantidadesPorCodigo.size()) {
            Set<String> encontrados = productos.stream()
                    .map(Producto::getCodigoBarras).collect(Collectors.toSet());
            String faltantes = cantidadesPorCodigo.keySet().stream()
                    .filter(cb -> !encontrados.contains(cb))
                    .collect(Collectors.joining(", "));
            throw new EntityNotFoundException("Productos no encontrados: " + faltantes);
        }

        Map<String, Producto> porCodigo = productos.stream()
                .collect(Collectors.toMap(Producto::getCodigoBarras, p -> p));

        // Cabecera del movimiento
        MovimientoInventario mov = new MovimientoInventario();
        mov.setTipo(tipo);
        mov.setFecha(LocalDateTime.now());
        mov.setComentario(comentario);
        mov.setLineas(new ArrayList<>());

        // Líneas
        cantidadesPorCodigo.forEach((codigo, cant) -> {
            MovimientoLinea ml = new MovimientoLinea();
            ml.setMovimiento(mov);
            ml.setProducto(porCodigo.get(codigo));
            ml.setCantidad(cant);
            mov.getLineas().add(ml);
        });

        try {
            // Los triggers/constraints de BD ajustan stock y validan insuficiencia
            return movRepo.save(mov);
        } catch (DataIntegrityViolationException ex) {
            String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
            if (msg != null && msg.toLowerCase().contains("stock insuficiente")) {
                throw new IllegalStateException("Stock insuficiente para una o más líneas de la SALIDA.", ex);
            }
            throw ex;
        } catch (PersistenceException | DataAccessException ex) {
            throw ex;
        }
    }

    // =========================
    //   ALERTAS DE INVENTARIO
    // =========================

    /**
     * Devuelve todas las alertas (críticas y cercanas al mínimo), ordenadas por menor stock.
     * Regla:
     *  - Crítico (bg-danger): stock <= mínimo
     *  - Cercano (bg-warning): stock <= mínimo + margen( max(5, 20% del mínimo) )
     */
    @Transactional(readOnly = true)
    public List<AlertaDTO> obtenerAlertas() {
        return productoRepo.findAll().stream()
                .filter(p -> p.getStockMinimo() != null)
                .map(p -> {
                    int stock = p.getStockActual() == null ? 0 : p.getStockActual();
                    int min = p.getStockMinimo();
                    int margen = Math.max(5, (int) (min * 0.2));

                    if (stock <= min) {
                        AlertaDTO a = new AlertaDTO();
                        a.setNombreProducto(p.getNombre());
                        a.setStock(stock);
                        a.setImagenUrl(p.getImagenUrl());
                        a.setColor("bg-danger");
                        return a;
                    } else if (stock <= min + margen) {
                        AlertaDTO a = new AlertaDTO();
                        a.setNombreProducto(p.getNombre());
                        a.setStock(stock);
                        a.setImagenUrl(p.getImagenUrl());
                        a.setColor("bg-warning");
                        return a;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(AlertaDTO::getStock)) // más urgentes primero
                .toList();
    }

    /** Top N productos más críticos (menos stock) para el popup. */
    @Transactional(readOnly = true)
    public List<AlertaDTO> obtenerAlertasTop(int n) {
        List<AlertaDTO> todas = obtenerAlertas();
        return todas.size() > n ? todas.subList(0, n) : todas;
    }

    /** Cantidad total de alertas para el badge de la campana. */
    @Transactional(readOnly = true)
    public int contarAlertas() {
        return obtenerAlertas().size();
    }
}
