package com.inventario1.Inventario.services.dto;

// bulk/dto/ProductoItemForm.java
import com.inventario1.Inventario.models.Categoria;
import com.inventario1.Inventario.models.UnidadBase;
import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public class ProductoItemForm {
    @NotBlank private String nombre;
    private String marca;
    @NotNull private Categoria categoria;
    @NotNull private UnidadBase unidadBase;
    @PositiveOrZero private Integer volumenNominalMl;
    @PositiveOrZero private Double graduacionAlcoholica;
    private LocalDate fechaVencimiento;
    @PositiveOrZero private Integer stockActual;
    @PositiveOrZero private Integer stockMinimo;
    @NotBlank @Size(max = 32) private String codigoBarras;
    private boolean perecible;
    private boolean retornable;
    private boolean activo = true;
    private MultipartFile imagen; // opcional

    // getters / setters
}
