package com.inventario1.Inventario.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "usuarios")
@Data
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password; // se almacenará encriptada con BCrypt

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String rol;
    // Ejemplo: "ADMIN", "BODEGA", "BAR", "ENCARGADO"

    private boolean activo = true;
}
