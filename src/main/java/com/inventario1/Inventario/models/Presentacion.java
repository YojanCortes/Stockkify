package com.inventario1.Inventario.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "PRESENTACIONES",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"producto_id", "nombrePresentacion"}),
                @UniqueConstraint(columnNames = {"sku"})
        })
@Data
public class Presentacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Producto lógico al que pertenece esta presentación
    @ManyToOne(optional=false, fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    // Ej.: "Unidad 355 ml", "Sixpack 6x355 ml", "Caja 24x355 ml"
    @Column(nullable=false, length=120)
    private String nombrePresentacion;

    // Identificadores comerciales
    @Column(length=50)
    private String sku;

    @Column(length=50)
    private String ean; // código de barras (opcional)

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private TipoPresentacion tipoPresentacion; // UNIDAD, PACK, CAJA, KEG, PORCION

    /**
     * Cantidad de UNIDAD BASE que representa 1 presentación.
     * Ejemplos:
     *  - Para "Unidad 355 ml" y unidadBase=ML => 355
     *  - Sixpack 6x355 ml => 2130
     *  - Caja 24x355 ml => 8520
     *  - Si unidadBase=UNIDAD (snack unidad), una “Caja 12 unid” => 12
     *  - Si unidadBase=GR y una porción son 200 g => 200
     */
    @Column(nullable=false)
    private Long factorABase;
}
