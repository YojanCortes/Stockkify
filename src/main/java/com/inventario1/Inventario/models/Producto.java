package com.inventario1.Inventario.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "productos")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Producto {

    @Id
    @Column(name = "codigo_barras", length = 32, nullable = false)
    private String codigoBarras; // PK basada en código de barras

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "marca")
    private String marca;

    @Column(name = "categoria")
    private String categoria;

    @Column(name = "unidad_base")
    private String unidadBase; // ML, etc.

    @Column(name = "volumen_nominal_ml")
    private Integer volumenNominalMl;

    @Column(name = "graduacion_alcoholica")
    private Integer graduacionAlcoholica;

    @Column(name = "perecible")
    private Boolean perecible;

    @Column(name = "retornable")
    private Boolean retornable;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad; // stock actual

    @Column(name = "stock_minimo")
    private Integer stockMinimo;

    @Column(name = "activo")
    private Boolean activo;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
}
