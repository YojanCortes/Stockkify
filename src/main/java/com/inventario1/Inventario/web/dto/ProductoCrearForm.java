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
    private String nombre;

    @Size(max = 120)
    private String marca;

    @NotNull
    private Categoria categoria;

    @NotNull
    private UnidadBase unidadBase;

    @Min(0)
    private Integer volumenNominalMl;

    @DecimalMin(value = "0.0", inclusive = true)
    private Double graduacionAlcoholica;

    private LocalDate fechaVencimiento;

    @Min(0)
    private Integer stockActual;

    @Min(0)
    private Integer stockMinimo;

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "\\d{8,32}", message = "Debe contener solo dígitos (8 a 32).")
    private String codigoBarras;

    private Boolean perecible = false;
    private Boolean retornable = false;
    private Boolean activo = true;

    // campo de carga de imagen
    private MultipartFile imagen;
}
