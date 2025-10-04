package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.models.TipoMovimiento;

public interface MovimientosService {
    void registrarMovimiento(Producto producto,
                             TipoMovimiento tipo,
                             int cantidad,
                             String comentario,
                             String referencia);
}
