package com.inventario1.Inventario.services.impl;

import com.inventario1.Inventario.models.MovimientoInventario;
import com.inventario1.Inventario.models.MovimientoLinea;
import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.TipoMovimiento;
import com.inventario1.Inventario.repos.MovimientoInventarioRepository;
import com.inventario1.Inventario.repos.MovimientoLineaRepository;
import com.inventario1.Inventario.repos.ProductoRepository;
import com.inventario1.Inventario.services.MovimientosService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class MovimientosServiceImpl implements MovimientosService {

    final MovimientoInventarioRepository movimientoInventarioRepository;
    final MovimientoLineaRepository movimientoLineaRepository;
    final ProductoRepository productoRepository;

    private String ensureUniqueRef(String base) {
        String ref = base;
        int i = 1;
        while (movimientoInventarioRepository.existsByReferencia(ref)) {
            ref = base + "-" + i++;
            if (i > 50) {
                ref = base + "-" + System.currentTimeMillis();
                break;
            }
        }
        return ref;
    }

    @Transactional
    @Override
    public void registrarMovimiento(Producto producto,
                                    TipoMovimiento tipo,
                                    int cantidad,
                                    String comentario,
                                    String referencia) {
        if (producto == null || cantidad <= 0) return;

        int actual = producto.getStockActual() == null ? 0 : producto.getStockActual();
        int nuevo = switch (tipo) {
            case ENTRADA -> actual + cantidad;
            case SALIDA  -> Math.max(0, actual - cantidad);
            default      -> actual;
        };
        producto.setStockActual(nuevo);
        productoRepository.save(producto);

        String base = (referencia == null || referencia.isBlank())
                ? "UI:" + tipo + ":" + producto.getCodigoBarras() + ":" + System.currentTimeMillis()
                : referencia;
        String refUnica = ensureUniqueRef(base);

        MovimientoInventario cab = new MovimientoInventario();
        cab.setTipo(tipo);
        cab.setFecha(LocalDateTime.now());
        cab.setComentario((comentario == null || comentario.isBlank()) ? "Ajuste de stock" : comentario.trim());
        cab.setReferencia(refUnica);
        movimientoInventarioRepository.save(cab);

        MovimientoLinea linea = new MovimientoLinea();
        linea.setMovimiento(cab);
        linea.setProducto(producto);
        linea.setCantidad(cantidad);
        movimientoLineaRepository.save(linea);
    }
}
