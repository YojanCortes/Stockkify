package com.inventario1.Inventario.services.dto;

import java.time.LocalDate;

public record BajaRotacionAgg(
        Long productoId, String nombre, String categoria,
        Integer stock, Double costoUnitario,
        Long sumCantidad, LocalDate ultimaVenta
) {}
