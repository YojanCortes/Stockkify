// path: src/main/java/com/inventario1/Inventario/services/dto/FilaProducto.java
package com.inventario1.Inventario.services.dto;

import com.inventario1.Inventario.models.Categoria;
import com.inventario1.Inventario.models.UnidadBase;
import lombok.*;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class FilaProducto {
    private long linea;

    // Requeridos
    private String codigoBarras;
    private String nombre;
    private String marca;
    private Integer precio;           // ajusta si usas BigDecimal en entidad
    private Categoria categoria;
    private UnidadBase unidadBase;

    // Opcionales
    private Integer volumenNominalMl;
    private Double graduacionAlcoholica;
    private Boolean perecible;
    private Boolean retornable;
    private Integer stockMinimo;
    private Boolean activo;

    // Error de validación por fila
    private String error;

    public boolean isValid() {
        return error == null
                && codigoBarras != null && !codigoBarras.isBlank()
                && nombre != null && !nombre.isBlank()
                && marca != null && !marca.isBlank()
                && precio != null
                && categoria != null
                && unidadBase != null;
    }
}
