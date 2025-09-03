package com.inventario1.Inventario.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "movimientos_inventario",
        indexes = {
                @Index(name = "idx_mov_prod_fecha", columnList = "producto_codigo, creado_en")
        }
)
@Data
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con Producto (FK a productos.codigo_barras)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "producto_codigo",               // columna existente en la tabla
            referencedColumnName = "codigo_barras", // PK de Producto
            nullable = false
    )
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimiento tipo; // ENTRADA / SALIDA / AJUSTE

    @Column(nullable = false)
    private Integer cantidad; // positiva

    @Column(length = 120)
    private String motivo;

    @Column(length = 120)
    private String referencia;

    @Column(length = 80)
    private String usuario;

    @Column(name = "stock_resultante", nullable = false)
    private Integer stockResultante;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;
}
