package com.inventario1.Inventario.services.dto;

import lombok.Data;

@Data
public class AlertaDTO {
    private String nombreProducto;
    private String imagenUrl; // opcional
    private int stock;
    private String color; // "bg-warning" o "bg-danger"
}