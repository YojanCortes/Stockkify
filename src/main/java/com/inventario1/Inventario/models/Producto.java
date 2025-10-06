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
    @Column(name = "id")
    private Long id;

    @Column(name = "codigo_barras", length = 32, nullable = false, unique = true)
    private String codigoBarras;

    @Column(nullable = false)
    private String nombre;

    private String marca;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private Categoria categoria = Categoria.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_base", nullable = false)
    private UnidadBase unidadBase;

    @Column(name = "graduacion_alcoholica")
    private Double graduacionAlcoholica;

    private Integer volumenNominalMl;

    @Builder.Default
    @Column(nullable = false)
    private Boolean perecible = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean retornable = false;

    @Builder.Default
    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual = 0;

    private Integer stockMinimo;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    // ===== Precio entero =====
    @Builder.Default
    @Column(name = "precio", nullable = false)
    private Integer precio = 0;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Version
    private Long version;

    // --- Imagen en BD ---
    @Lob
    @Column(name = "imagen", columnDefinition = "LONGBLOB")
    private byte[] imagen;

    @Column(name = "imagen_content_type", length = 100)
    private String imagenContentType;

    @Column(name = "imagen_nombre", length = 255)
    private String imagenNombre;

    @Column(name = "imagen_tamano")
    private Long imagenTamano;

    @Column(name = "imagen_url", length = 255)
    private String imagenUrl;

    @PrePersist
    public void prePersist() {
        var now = LocalDateTime.now();
        if (stockActual == null) stockActual = 0;
        if (perecible == null) perecible = false;
        if (retornable == null) retornable = false;
        if (activo == null) activo = true;
        if (precio == null) precio = 0;
        creadoEn = now;
        actualizadoEn = now;
    }

    @PreUpdate
    public void preUpdate() {
        if (stockActual == null) stockActual = 0;
        if (perecible == null) perecible = false;
        if (retornable == null) retornable = false;
        if (activo == null) activo = true;
        if (precio == null) precio = 0;
        actualizadoEn = LocalDateTime.now();
    }
}
