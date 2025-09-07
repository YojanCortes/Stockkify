// src/main/java/com/inventario1/Inventario/web/EmpleadoForm.java
package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Rol;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class EmpleadoForm {
    @NotBlank(message = "El RUT es obligatorio")
    private String rut;               // <- NUEVO (se envía con o sin guion, se normaliza)

    @NotBlank @Size(max = 120)
    private String nombre;

    @NotBlank @Email @Size(max = 120)
    private String email;

    @Size(max = 32)
    private String telefono;

    @NotNull
    private Rol rol;

    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    private String confirmPassword;

    private Boolean activo = true;
    private Boolean requiereCambioPassword = false;

    @Size(max = 120)
    private String profesion;
}
