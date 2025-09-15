package com.inventario1.Inventario.web.dto;

import com.inventario1.Inventario.models.Categoria;
import com.inventario1.Inventario.models.UnidadBase;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Data
public class ProductoEditarForm {
    @NotBlank
    private String codigoBarras;

    @NotBlank
    private String nombre;

    private String marca;

    @NotNull
    private Categoria categoria;

    @NotNull
    private UnidadBase unidadBase;

    private Integer volumenNominalMl;
    private Double graduacionAlcoholica;

    // NUEVO
    @NotNull
    @Min(0)
    private Integer stockActual = 0;

    private Integer stockMinimo;
    private Boolean perecible = false;
    private Boolean retornable = false;
    private LocalDate fechaVencimiento;
    private Boolean activo = true;

    private MultipartFile imagen;
}
