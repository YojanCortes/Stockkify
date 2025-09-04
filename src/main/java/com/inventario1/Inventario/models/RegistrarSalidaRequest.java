package com.inventario1.Inventario.models;

// src/main/java/com/tuapp/inventario/dto/RegistrarSalidaRequest.java

import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class RegistrarSalidaRequest {
    public String motivo;
    public String referencia;
    public String usuario;

    @NotNull
    public List<Item> items;

    public static class Item {
        @NotNull public String codigo;
        @Positive public int cantidad;
    }
}


