package com.inventario1.Inventario.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "productos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") // PK real en la BD
    private Long id;

    @Column(name = "codigo_barras", length = 32, nullable = false, unique = true)
    private String codigoBarras;

    @Column(nullable = false)
    private String nombre;

    private String marca;

    // Drop-down en la vista (puede ser String o Enum; por ahora String)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    @Builder.Default
    private Categoria categoria = Categoria.GENERAL;

    private String unidadBase;

    // Datos opcionales según tu dominio
    private Integer volumenNominalMl;
    private Integer graduacionAlcoholica;

    @Column(nullable = false)
    private boolean perecible;

    @Column(nullable = false)
    private boolean retornable;

    // Cantidad SIEMPRE no nula para que la vista no explote
    @Builder.Default
    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual = 0;

    // Puede ser null para indicar “sin mínimo definido”
    private Integer stockMinimo;

    // Nuevo: fecha de vencimiento (puede ser null)
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Builder.Default
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
        if (stockActual == null) stockActual = 0;
        creadoEn = now;
        actualizadoEn = now;
    }

    @PreUpdate
    public void preUpdate() {
        if (stockActual == null) stockActual = 0;
        actualizadoEn = LocalDateTime.now();
    }
}
