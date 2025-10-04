package com.inventario1.Inventario.services.dto;

import com.inventario1.Inventario.models.TipoMovimiento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;

public class MovimientoItemDTO {

    private Long movimientoId;
    private Long lineaId;
    private String codigoBarras;
    private String nombreProducto;
    private TipoMovimiento tipo;
    private LocalDateTime fechaDateTime; // si tu entidad usa LocalDateTime
    private LocalDate fechaDate;         // si tu entidad usa LocalDate
    private Integer cantidad;

    // Constructor usado si m.fecha es LocalDateTime
    public MovimientoItemDTO(Long movimientoId,
                             Long lineaId,
                             String codigoBarras,
                             String nombreProducto,
                             TipoMovimiento tipo,
                             LocalDateTime fecha,
                             Integer cantidad) {
        this.movimientoId = movimientoId;
        this.lineaId = lineaId;
        this.codigoBarras = codigoBarras;
        this.nombreProducto = nombreProducto;
        this.tipo = tipo;
        this.fechaDateTime = fecha;
        this.cantidad = cantidad;
    }

    // Constructor usado si m.fecha es LocalDate
    public MovimientoItemDTO(Long movimientoId,
                             Long lineaId,
                             String codigoBarras,
                             String nombreProducto,
                             TipoMovimiento tipo,
                             LocalDate fecha,
                             Integer cantidad) {
        this.movimientoId = movimientoId;
        this.lineaId = lineaId;
        this.codigoBarras = codigoBarras;
        this.nombreProducto = nombreProducto;
        this.tipo = tipo;
        this.fechaDate = fecha;
        this.cantidad = cantidad;
    }

    // === Getters usados en la vista ===
    public Long getMovimientoId() { return movimientoId; }
    public Long getLineaId() { return lineaId; }
    public String getCodigoBarras() { return codigoBarras; }
    public String getNombreProducto() { return nombreProducto; }
    public TipoMovimiento getTipo() { return tipo; }
    public Integer getCantidad() { return cantidad; }

    // Para Thymeleaf: ambos (LocalDate y LocalDateTime) implementan TemporalAccessor
    public TemporalAccessor getFecha() {
        return (fechaDateTime != null) ? fechaDateTime : fechaDate;
    }

    // Compatibilidad con plantillas que esperen "item.nombre"
    public String getNombre() { return nombreProducto; }
}
