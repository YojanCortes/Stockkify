package com.inventario1.Inventario.services.impl;

import com.inventario1.Inventario.models.MovimientoInventario;
import com.inventario1.Inventario.models.MovimientoLinea;
import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.TipoMovimiento;
import com.inventario1.Inventario.repos.MovimientoInventarioRepository;
import com.inventario1.Inventario.repos.MovimientoLineaRepository;
import com.inventario1.Inventario.services.MovimientosService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MovimientosServiceImpl implements MovimientosService {

    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final MovimientoLineaRepository movimientoLineaRepository;

    /** Genera una referencia única y, si ya existe, agrega sufijo incremental. */
    private String ensureUniqueRef(String base) {
        String ref = base;
        int i = 1;
        while (movimientoInventarioRepository.existsByReferencia(ref)) {
            ref = base + "-" + i++;
            if (i > 50) { // red de seguridad
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

        // base única por defecto si no te pasan referencia
        String base = (referencia == null || referencia.isBlank())
                ? "UI:" + tipo + ":" + producto.getCodigoBarras() + ":" + System.currentTimeMillis()
                : referencia;
        String refUnica = ensureUniqueRef(base);

        MovimientoInventario cab = new MovimientoInventario();
        cab.setTipo(tipo);
        cab.setFecha(LocalDateTime.now());   // si tu campo es LocalDate, usa LocalDate.now()
        cab.setComentario(comentario);
        cab.setReferencia(refUnica);
        movimientoInventarioRepository.save(cab);

        MovimientoLinea linea = new MovimientoLinea();
        linea.setMovimiento(cab);
        linea.setProducto(producto);
        linea.setCantidad(cantidad);
        movimientoLineaRepository.save(linea);
    }
}
