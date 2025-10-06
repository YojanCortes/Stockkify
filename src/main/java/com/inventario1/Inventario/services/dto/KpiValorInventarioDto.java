package com.inventario1.Inventario.services.dto;

import java.util.List;

public record KpiValorInventarioDto(
        long totalItems,
        long promedioPorItem,
        long totalValorCLP,
        List<CategoriaValorDto> categorias
) {
    public record CategoriaValorDto(String categoria, long valorCLP) {}
}