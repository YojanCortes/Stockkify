package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.MovimientoInventario;
import com.inventario1.Inventario.models.MovimientoLinea;
import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.TipoMovimiento;
import com.inventario1.Inventario.repos.MovimientoInventarioRepository;
import com.inventario1.Inventario.repos.MovimientoLineaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MovimientosServiceImpl implements MovimientosService {

    private final MovimientoInventarioRepository movInvRepo;
    private final MovimientoLineaRepository movLineaRepo;

    @Override
    @Transactional
    public void registrarMovimiento(Producto producto,
                                    TipoMovimiento tipo,
                                    int cantidad,
                                    String comentario,
                                    String referencia) {

        final String ref = (referencia != null && !referencia.isBlank())
                ? referencia
                : "UI:" + (producto != null ? producto.getCodigoBarras() : "sin-cb") + ":" + System.currentTimeMillis();

        if (movInvRepo.existsByReferencia(ref)) {
            return; // idempotencia: no duplicar
        }

        // Cabecera
        MovimientoInventario cab = new MovimientoInventario();
        cab.setTipo(tipo);                    // ENTRADA / SALIDA / AJUSTE
        cab.setFecha(LocalDateTime.now());
        cab.setComentario(comentario);
        cab.setReferencia(ref);
        cab = movInvRepo.save(cab);

        // Línea
        MovimientoLinea linea = new MovimientoLinea();
        linea.setMovimiento(cab);             // si tu entidad usa relación @ManyToOne
        linea.setProducto(producto);
        linea.setCantidad(cantidad);

        // Si tus entidades usan IDs (sin relación), cambia por:
        // linea.setMovimientoId(cab.getId());
        // linea.setProductoId(producto.getId());

        movLineaRepo.save(linea);
    }
}
