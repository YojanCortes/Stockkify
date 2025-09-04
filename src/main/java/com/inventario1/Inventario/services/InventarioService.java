package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.*;
import com.inventario1.Inventario.repos.MovimientoInventarioRepository;
import com.inventario1.Inventario.repos.ProductoRepository;
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

    /** Listado paginado de productos (sin filtro). */
    @Transactional(readOnly = true)
    public Page<Producto> listarProductos(Pageable pageable) {
        return productoRepo.findAll(pageable);
    }

    /** Búsqueda paginada por nombre o código de barras. */
    @Transactional(readOnly = true)
    public Page<Producto> buscarProductos(String q, Pageable pageable) {
        if (q == null || q.isBlank()) return listarProductos(pageable);
        return productoRepo.search(q.trim(), pageable);
    }

    /** Lee un producto por su código de barras. */
    @Transactional(readOnly = true)
    public Producto leerProducto(String codigoBarras) {
        return productoRepo.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + codigoBarras));
    }

    // =========================
    //   REGISTRO DE MOVIMIENTOS
    // =========================

    /** Atajo: registra SALIDA de un solo producto. */
    @Transactional
    public MovimientoInventario registrarSalida(String codigoBarras, int cantidad, String comentario) {
        return registrarSalida(List.of(new LineaMovimientoInput(codigoBarras, cantidad)), comentario);
    }

    /** Atajo: registra ENTRADA de un solo producto. */
    @Transactional
    public MovimientoInventario registrarEntrada(String codigoBarras, int cantidad, String comentario) {
        return registrarEntrada(List.of(new LineaMovimientoInput(codigoBarras, cantidad)), comentario);
    }

    /** SALIDA con múltiples líneas. Los triggers validan stock y descuentan. */
    @Transactional
    public MovimientoInventario registrarSalida(List<LineaMovimientoInput> items, String comentario) {
        return registrarMovimiento(TipoMovimiento.SALIDA, items, comentario);
    }

    /** ENTRADA con múltiples líneas. Los triggers suman stock. */
    @Transactional
    public MovimientoInventario registrarEntrada(List<LineaMovimientoInput> items, String comentario) {
        return registrarMovimiento(TipoMovimiento.ENTRADA, items, comentario);
    }

    /** (Opcional) AJUSTE: por defecto no cambia stock (según triggers). */
    @Transactional
    public MovimientoInventario registrarAjuste(List<LineaMovimientoInput> items, String comentario) {
        return registrarMovimiento(TipoMovimiento.AJUSTE, items, comentario);
    }

    // -------------------------
    // Implementación común
    // -------------------------
    private MovimientoInventario registrarMovimiento(TipoMovimiento tipo,
                                                     List<LineaMovimientoInput> items,
                                                     String comentario) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Debe indicar al menos una línea.");
        }
        // Validaciones básicas y normalización (agregar cantidades de códigos duplicados)
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

        // Cargar productos en bloque
        List<Producto> productos = productoRepo.findAllByCodigoBarrasIn(cantidadesPorCodigo.keySet());
        if (productos.size() != cantidadesPorCodigo.size()) {
            // Encontrar faltantes
            Set<String> encontrados = productos.stream()
                    .map(Producto::getCodigoBarras).collect(Collectors.toSet());
            String faltantes = cantidadesPorCodigo.keySet().stream()
                    .filter(cb -> !encontrados.contains(cb))
                    .collect(Collectors.joining(", "));
            throw new EntityNotFoundException("Productos no encontrados: " + faltantes);
        }
        // Map por código para acceso rápido
        Map<String, Producto> porCodigo = productos.stream()
                .collect(Collectors.toMap(Producto::getCodigoBarras, p -> p));

        // Construir cabecera
        MovimientoInventario mov = new MovimientoInventario();
        mov.setTipo(tipo);
        mov.setFecha(LocalDateTime.now());
        mov.setComentario(comentario);
        mov.setLineas(new ArrayList<>());

        // Construir líneas (cascade ALL en MovimientoInventario)
        cantidadesPorCodigo.forEach((codigo, cant) -> {
            MovimientoLinea ml = new MovimientoLinea();
            ml.setMovimiento(mov);
            ml.setProducto(porCodigo.get(codigo));
            ml.setCantidad(cant);
            mov.getLineas().add(ml);
        });

        try {
            // Insertará cabecera y luego líneas; triggers ajustan stock
            return movRepo.save(mov);
        } catch (DataIntegrityViolationException ex) {
            // Si el trigger lanzó SIGNAL '45000' con "Stock insuficiente..."
            String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
            if (msg != null && msg.toLowerCase().contains("stock insuficiente")) {
                throw new IllegalStateException("Stock insuficiente para una o más líneas de la SALIDA.", ex);
            }
            throw ex;
        } catch (PersistenceException | DataAccessException ex) {
            throw ex; // deja que la capa superior lo trate/loggee
        }
    }
}
