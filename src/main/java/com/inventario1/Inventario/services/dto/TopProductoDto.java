package com.inventario1.Inventario.services.dto;

/** DTO para el KPI 7 — Top 5 Productos Más Vendidos */
public record TopProductoDto(
        String codigoBarras,
        String nombre,
        String categoria,
        long unidades
) {}
