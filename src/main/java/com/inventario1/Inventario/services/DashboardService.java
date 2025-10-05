package com.inventario1.Inventario.services;


import com.inventario1.Inventario.repos.DashboardRepository;
import com.inventario1.Inventario.services.dto.KpiRupturaDto;
import com.inventario1.Inventario.services.dto.ProductoSinStockDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardRepository repo;

    public DashboardService(DashboardRepository repo) {
        this.repo = repo;
    }

    public KpiRupturaDto obtenerKpiRuptura() {
        long activos = repo.countProductosActivos();
        long sinStock = repo.countProductosSinStock();

        double porcentaje = 0.0;
        if (activos > 0) {
            porcentaje = (sinStock * 100.0) / activos;
        }

        return new KpiRupturaDto(activos, sinStock, porcentaje);
    }

    public List<ProductoSinStockDto> obtenerProductosSinStock() {
        List<Object[]> rows = repo.listarProductosSinStock();
        List<ProductoSinStockDto> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long id             = r[0] != null ? ((Number) r[0]).longValue() : null;
            String codigoBarras = r[1] != null ? r[1].toString() : null;
            String nombre       = r[2] != null ? r[2].toString() : null;
            String categoria    = r[3] != null ? r[3].toString() : null;
            Integer stockActual = r[4] != null ? ((Number) r[4]).intValue() : 0;

            out.add(new ProductoSinStockDto(id, codigoBarras, nombre, categoria, stockActual));
        }
        return out;
    }
}
