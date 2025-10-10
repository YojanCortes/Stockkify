package com.inventario1.Inventario.services.dto;


public record BajaRotacionItem(
        String nombre, String categoria,
        double ventasMensuales, int stock, int diasSinVenta, double costoTotal
) {}
