package com.inventario1.Inventario.services.dto;

import java.time.LocalDate;

/** Punto de la serie semanal: inicio de semana ISO y unidades consumidas. */
public record SemanalPuntoDto(
        LocalDate semanaInicio,
        long unidades
) {}
