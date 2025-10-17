package com.inventario1.Inventario.web.dto;

import com.inventario1.Inventario.models.Categoria;
import com.inventario1.Inventario.models.UnidadBase;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Data
public class ProductoCrearForm {

    @NotBlank
    @Size(max = 200)
    String nombre;

    @Size(max = 120)
    String marca;

    @NotNull
    Categoria categoria;

    @NotNull
    UnidadBase unidadBase;

    @Min(0)
    Integer volumenNominalMl;

    @DecimalMin(value = "0.0", inclusive = true)
    Double graduacionAlcoholica;

    LocalDate fechaVencimiento;

    @Min(0)
    Integer stockActual;

    @Min(0)
    Integer stockMinimo;

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "\\d{8,32}", message = "Debe contener solo dígitos (8 a 32).")
    String codigoBarras;

    @NotNull
    @Min(0)
    Integer precio;      // ← NUEVO (binding de th:field="*{precio}")

    @NotNull
    @Min(0)
    Integer cantidad;    // ← NUEVO (binding de th:field="*{cantidad}")

    Boolean perecible = false;
    Boolean retornable = false;
    Boolean activo = true;

    MultipartFile imagen;
}
