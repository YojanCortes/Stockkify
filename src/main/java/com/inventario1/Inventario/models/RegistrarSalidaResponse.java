package com.inventario1.Inventario.models;

// src/main/java/com/tuapp/inventario/dto/RegistrarSalidaResponse.java

import java.util.ArrayList;
import java.util.List;

public class RegistrarSalidaResponse {
    public boolean ok;
    public int registrados; // número de líneas insertadas
    public Long movimientoId;
    public List<String> errores = new ArrayList<>();
}
