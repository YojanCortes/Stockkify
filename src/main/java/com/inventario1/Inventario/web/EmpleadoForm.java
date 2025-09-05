package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Rol;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmpleadoForm {

    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
    private String nombre;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Debe ser un correo válido")
    @Size(max = 120, message = "El correo no puede superar los 120 caracteres")
    private String email;

    @Size(max = 32, message = "El teléfono no puede superar los 32 caracteres")
    private String telefono;

    @NotNull(message = "Debes seleccionar un rol")
    private Rol rol;

    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    // Usado solo para validación en el formulario/controlador (no se persiste)
    private String confirmPassword;

    private Boolean activo = true;
    private Boolean requiereCambioPassword = false;

    @Size(max = 120, message = "La profesión no puede superar los 120 caracteres")
    private String profesion;

    // Validación opcional: si ambas están presentes, deben coincidir
    @AssertTrue(message = "Las contraseñas no coinciden")
    public boolean isPasswordConfirmada() {
        if (password == null || password.isBlank()) return true;          // password opcional (edición)
        if (confirmPassword == null || confirmPassword.isBlank()) return true; // deja al controlador exigir confirmación si hace falta
        return password.equals(confirmPassword);
    }
}
