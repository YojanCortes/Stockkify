package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.MovimientoInventario;
import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.TipoMovimiento;
import com.inventario1.Inventario.repos.MovimientoInventarioRepository;
import com.inventario1.Inventario.repos.ProductoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventarioService {

    private final ProductoRepository productoRepo;
    private final MovimientoInventarioRepository movRepo;

    public InventarioService(ProductoRepository productoRepo, MovimientoInventarioRepository movRepo) {
        this.productoRepo = productoRepo;
        this.movRepo = movRepo;
    }

    /** Listado paginado de productos (sin filtro). */
    @Transactional(readOnly = true)
    public Page<Producto> listarProductos(Pageable pageable) {
        return productoRepo.findAll(pageable);
    }

    /** Búsqueda paginada por nombre o código de barras (usa repo.search). */
    @Transactional(readOnly = true)
    public Page<Producto> buscarProductos(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return listarProductos(pageable);
        }
        return productoRepo.search(q.trim(), pageable);
    }

    /** Lee un producto por su código de barras (PK). */
    @Transactional(readOnly = true)
    public Producto leerProducto(String codigoBarras) {
        return productoRepo.findById(codigoBarras)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + codigoBarras));
    }

    /** Registra una SALIDA (descuenta stock) y guarda el movimiento (kardex). */
    @Transactional
    public MovimientoInventario registrarSalida(String codigoBarras, int cantidad,
                                                String motivo, String referencia, String usuario) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser > 0");
        }

        // 1) Verifica existencia
        Producto p = leerProducto(codigoBarras);

        // 2) Descuento atómico
        int filas = productoRepo.descontarStock(codigoBarras, cantidad);
        if (filas == 0) {
            Integer actual = productoRepo.findById(codigoBarras).map(Producto::getCantidad).orElse(0);
            throw new IllegalStateException("Stock insuficiente. Disponible: " + actual + ", solicitado: " + cantidad);
        }

        // 3) Lee stock resultante
        int stockResultante = productoRepo.findById(codigoBarras)
                .map(Producto::getCantidad)
                .orElseThrow(() -> new IllegalStateException("No se pudo leer stock tras salida"));

        // 4) Registra movimiento
        MovimientoInventario m = new MovimientoInventario();
        m.setProducto(p);
        m.setTipo(TipoMovimiento.SALIDA);
        m.setCantidad(cantidad);
        m.setMotivo(motivo);
        m.setReferencia(referencia);
        m.setUsuario(usuario);
        m.setStockResultante(stockResultante);

        return movRepo.save(m);
    }
}
