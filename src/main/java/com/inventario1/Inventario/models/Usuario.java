// src/main/java/com/inventario1/Inventario/models/Usuario.java
package com.inventario1.Inventario.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @Column(name = "rut", length = 12)
    private String rut;

    @Column(name = "username", unique = true, nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false)
    private Rol rol;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "telefono", length = 32)
    private String telefono;

    @Column(name = "profesion", length = 120)
    private String profesion;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Builder.Default
    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;

    @Builder.Default
    @Column(name = "requiere_cambio_password", nullable = false)
    private Boolean requiereCambioPassword = false;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_expira")
    private LocalDateTime resetExpira;

    @Column(name = "password_actualizado_en")
    private LocalDateTime passwordActualizadoEn;

    // ===== Imagen de perfil (guardada en BD) =====
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "foto")
    private byte[] foto;

    @Column(name = "foto_content_type", length = 100)
    private String fotoContentType;

    @Column(name = "foto_nombre", length = 255)
    private String fotoNombre;

    @Column(name = "foto_tamano")
    private Long fotoTamano;

    // ===== Timestamps obligatorios =====
    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Garantiza valores por si el proveedor ignora anotaciones
    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (creadoEn == null) creadoEn = now;
        if (actualizadoEn == null) actualizadoEn = now;
        if (passwordActualizadoEn == null) passwordActualizadoEn = now;
        if (intentosFallidos == null) intentosFallidos = 0;
        if (activo == null) activo = true;
        if (requiereCambioPassword == null) requiereCambioPassword = false;
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = LocalDateTime.now();
    }
}
