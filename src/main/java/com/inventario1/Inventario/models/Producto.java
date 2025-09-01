package com.inventario1.Inventario.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "productos",
        indexes = {
                @Index(name = "idx_productos_nombre", columnList = "nombre")
        }
)
@Data
public class Producto {

    // ID = CÓDIGO DE BARRAS (no se autogenera)
    @Id
    @Column(name = "codigo_barras", length = 32, nullable = false)
    private String codigoBarras;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 80)
    private String marca;

    @Column(length = 80)
    private String categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_base", nullable = false, length = 10)
    private UnidadBase unidadBase; // ML / GR / UNIDAD

    @Column(name = "volumen_nominal_ml")
    private Integer volumenNominalMl;

    @Column(name = "graduacion_alcoholica")
    private Double graduacionAlcoholica;

    @Column(nullable = false)
    private Boolean perecible = false;

    @Column(nullable = false)
    private Boolean retornable = false;

    @Column(name = "stock_minimo")
    private Integer stockMinimo;

    @Column(nullable = false)
    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
}
