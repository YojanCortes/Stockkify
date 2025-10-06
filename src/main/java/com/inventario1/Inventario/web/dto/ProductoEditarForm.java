package com.inventario1.Inventario.web.dto;

import com.inventario1.Inventario.models.Categoria;
import com.inventario1.Inventario.models.UnidadBase;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Min(0)
    private Integer volumenNominalMl; // opcional, pero si viene debe ser >= 0

    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private Double graduacionAlcoholica; // 0–100%

    /** Precio entero (por ejemplo, en pesos) */
    @NotNull
    @Min(0)
    private Integer precio = 0;

    // Inventario
    @NotNull
    @Min(0)
    private Integer stockActual = 0;

    @Min(0)
    private Integer stockMinimo;

    private Boolean perecible = false;
    private Boolean retornable = false;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaVencimiento;

    private Boolean activo = true;

    // Imagen (opcional)
    private MultipartFile imagen;

    // Permite borrar la imagen actual del producto
    private Boolean eliminarImagen = false;

    // Conveniencia
    public boolean hasImagen() {
        return imagen != null && !imagen.isEmpty();
    }
}
