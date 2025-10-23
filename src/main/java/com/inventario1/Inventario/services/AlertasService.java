// src/main/java/com/inventario1/Inventario/services/AlertasService.java
package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.services.dto.AlertaDTO;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class AlertasService {

    /** Rango para “cerca del mínimo”: hasta stockMinimo + NEAR_MIN_RANGE sale en amarillo. */
    private static final int NEAR_MIN_RANGE = 5;

    /** Devuelve SOLO alertas (warning/danger) a partir de productos activos. */
    public List<AlertaDTO> buildAlertasSoloActivos(List<Producto> activos) {
        return activos.stream()
                .map(this::toDtoOrNull)                // convierte a DTO o null si está OK
                .filter(dto -> dto != null)            // deja solo warning/danger
                .sorted(Comparator.comparingInt(AlertaDTO::getStock))
                .toList();
    }

    /** Mapea a DTO; si el producto está OK retorna null (para filtrarlo). */
    private AlertaDTO toDtoOrNull(Producto p) {
        int sa = nvl(p.getStockActual());
        int sm = nvl(p.getStockMinimo());

        String color;
        if (sa <= sm) {
            color = "bg-danger";
        } else if (sa <= sm + NEAR_MIN_RANGE) {
            color = "bg-warning";
        } else {
            return null; // estado OK, no es alerta
        }

        AlertaDTO dto = new AlertaDTO();
        dto.setNombreProducto(p.getNombre());
        dto.setStock(sa);
        dto.setColor(color);
        if (p.getCodigoBarras() != null && !p.getCodigoBarras().isBlank()) {
            dto.setImagenUrl("/img/productos/" + p.getCodigoBarras());
        }
        return dto;
    }

    private static int nvl(Integer v){ return v == null ? 0 : v; }
}
