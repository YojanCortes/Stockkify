// src/main/java/com/inventario1/Inventario/models/Usuario.java
package com.inventario1.Inventario.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
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

    /** Máximo permitido para la foto: 2 MB */
    public static final long MAX_FOTO_BYTES = 2L * 1024 * 1024;

    @Id
    @Column(name = "rut", length = 12, nullable = false)
    private String rut;

    @Column(name = "username", unique = true, nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 32)
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

    @Column(name = "reset_token", length = 255)
    private String resetToken;

    @Column(name = "reset_expira")
    private LocalDateTime resetExpira;

    @Column(name = "password_actualizado_en")
    private LocalDateTime passwordActualizadoEn;

    // ===== Imagen de perfil (guardada en BD, hasta 2 MB) =====
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Size(max = 2_097_152) // Bean Validation (2 MB) — se aplica si usas @Valid
    @Column(name = "foto", columnDefinition = "MEDIUMBLOB") // asegura espacio suficiente en MySQL
    private byte[] foto;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Column(name = "foto_content_type", length = 100)
    private String fotoContentType;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Column(name = "foto_nombre", length = 255)
    private String fotoNombre;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Column(name = "foto_tamano")
    private Long fotoTamano;

    // ===== Timestamps =====
    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // ====== Reglas y defaults ======
    @PrePersist
    void prePersist() {
        final LocalDateTime now = LocalDateTime.now();
        if (creadoEn == null) creadoEn = now;
        if (actualizadoEn == null) actualizadoEn = now;
        if (passwordActualizadoEn == null) passwordActualizadoEn = now;
        if (intentosFallidos == null) intentosFallidos = 0;
        if (activo == null) activo = true;
        if (requiereCambioPassword == null) requiereCambioPassword = false;

        // Seguridad por si alguien setea la foto directamente sin pasar por setters
        enforceFotoLimit();
        syncFotoMeta();
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = LocalDateTime.now();
        enforceFotoLimit();
        syncFotoMeta();
    }

    /** Setter seguro: valida tamaño y actualiza metadatos */
    public void setFoto(byte[] bytes) {
        if (bytes != null && bytes.length > MAX_FOTO_BYTES) {
            throw new IllegalArgumentException("La foto excede el máximo permitido de 2 MB.");
        }
        this.foto = bytes;
        syncFotoMeta();
    }

    /** Setter conveniente: contenido + content-type + nombre de archivo, con validación de 2 MB */
    public void setFoto(byte[] bytes, String contentType, String nombreArchivo) {
        setFoto(bytes); // valida 2 MB
        this.fotoContentType = (bytes == null) ? null : contentType;
        this.fotoNombre = (bytes == null) ? null : nombreArchivo;
    }

    /** Limpia completamente la foto y sus metadatos */
    public void clearFoto() {
        this.foto = null;
        this.fotoContentType = null;
        this.fotoNombre = null;
        this.fotoTamano = null;
    }

    // ====== Helpers internos ======
    private void enforceFotoLimit() {
        if (foto != null && foto.length > MAX_FOTO_BYTES) {
            throw new IllegalStateException("La foto excede el máximo permitido de 2 MB.");
        }
    }

    private void syncFotoMeta() {
        if (this.foto != null) {
            this.fotoTamano = (long) this.foto.length;
        } else {
            this.fotoTamano = null;
            this.fotoNombre = null;
            this.fotoContentType = null;
        }
    }
}
