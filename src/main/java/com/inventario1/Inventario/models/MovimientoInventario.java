package com.inventario1.Inventario.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "movimientos_inventario",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mi_referencia", columnNames = "referencia")
        },
        indexes = {
                @Index(name = "idx_mi_fecha", columnList = "fecha"),
                @Index(name = "idx_mi_tipo",  columnList = "tipo")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Referencia idempotente (p.ej. INIT:<codigo>:<fecha> o CSV:<hash>) */
    @Column(name = "referencia", length = 128, unique = true)
    private String referencia;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimiento tipo; // ENTRADA / SALIDA

    @Column(length = 255)
    private String comentario;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @OneToMany(mappedBy = "movimiento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovimientoLinea> lineas;

    @PrePersist
    public void prePersist() {
        var now = LocalDateTime.now();
        if (fecha == null) fecha = now;
        if (tipo == null) tipo = TipoMovimiento.ENTRADA; // valor seguro por defecto
        creadoEn = now;
        actualizadoEn = now;
    }

    @PreUpdate
    public void preUpdate() {
        actualizadoEn = LocalDateTime.now();
    }
}
