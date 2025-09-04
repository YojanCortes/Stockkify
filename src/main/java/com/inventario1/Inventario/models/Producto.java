package com.inventario1.Inventario.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "productos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")               // <- PK real en la BD
    private Long id;

    @Column(name = "codigo_barras", length = 32, nullable = false, unique = true)
    private String codigoBarras;

    @Column(nullable = false)
    private String nombre;

    private String marca;
    private String categoria;
    private String unidadBase;
    private Integer volumenNominalMl;
    private Integer graduacionAlcoholica;

    @Column(nullable = false)
    private boolean perecible;

    @Column(nullable = false)
    private boolean retornable;

    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual;

    private Integer stockMinimo;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Version
    private Long version;

    @PrePersist
    public void prePersist() {
        var now = LocalDateTime.now();
        creadoEn = now;
        actualizadoEn = now;
    }
    @PreUpdate
    public void preUpdate() {
        actualizadoEn = LocalDateTime.now();
    }
}
