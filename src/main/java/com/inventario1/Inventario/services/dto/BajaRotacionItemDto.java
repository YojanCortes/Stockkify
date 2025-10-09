// src/main/java/com/inventario1/Inventario/services/dto/BajaRotacionItemDto.java
package com.inventario1.Inventario.services.dto;

public record BajaRotacionItemDto(
        Long id,
        String nombre,
        String categoria,
        int ventasMensuales,
        int stock,
        Integer diasSinVenta,
        Double costoTotal
) {}
