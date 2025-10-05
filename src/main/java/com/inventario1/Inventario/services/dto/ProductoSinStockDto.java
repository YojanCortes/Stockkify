package com.inventario1.Inventario.services.dto;

public class ProductoSinStockDto {
    private Long id;
    private String codigoBarras;
    private String nombre;
    private String categoria;
    private Integer stockActual;

    public ProductoSinStockDto() {}

    public ProductoSinStockDto(Long id, String codigoBarras, String nombre, String categoria, Integer stockActual) {
        this.id = id;
        this.codigoBarras = codigoBarras;
        this.nombre = nombre;
        this.categoria = categoria;
        this.stockActual = stockActual;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public Integer getStockActual() { return stockActual; }
    public void setStockActual(Integer stockActual) { this.stockActual = stockActual; }
}
