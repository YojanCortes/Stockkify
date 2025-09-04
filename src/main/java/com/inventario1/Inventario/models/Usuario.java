package com.inventario1.Inventario.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cuenta
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = true, unique = true, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "requiere_cambio_password", nullable = false)
    private Boolean requiereCambioPassword = false;

    // Datos visibles
    @Column(nullable = false, length = 120)
    private String nombre; // puedes guardar "Nombre Apellido"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(nullable = false)
    private Boolean activo = true;

    // Auditoría / seguridad (opcionales)
    @Column(name = "intentos_fallidos")
    private Integer intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @Column(length = 120)         // null permitido
    private String profesion;

    @Column(name = "reset_token", length = 120)
    private String resetToken;

    @Column(name = "reset_expira")
    private LocalDateTime resetExpira;

    @Column(name = "password_actualizado_en")
    private LocalDateTime passwordActualizadoEn;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    // --- Helpers opcionales ---

    public String getNombreCorto() {
        if (nombre == null) return "";
        String[] parts = nombre.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        return parts[0] + " " + parts[parts.length - 1];
    }
}
