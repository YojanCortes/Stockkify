package com.inventario1.Inventario.services.dto;

import java.util.List;

/** Respuesta KPI 8: histórico semanal + pronóstico semanal. */
public record KpiConsumoForecastDto(
        List<SemanalPuntoDto> historico,
        List<SemanalPuntoDto> pronostico
) {}
