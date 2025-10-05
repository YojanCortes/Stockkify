package com.inventario1.Inventario.services.dto;


public class KpiRupturaDto {
    private long activos;
    private long sinStock;
    private double porcentaje;

    public KpiRupturaDto() {}

    public KpiRupturaDto(long activos, long sinStock, double porcentaje) {
        this.activos = activos;
        this.sinStock = sinStock;
        this.porcentaje = porcentaje;
    }

    public long getActivos() { return activos; }
    public void setActivos(long activos) { this.activos = activos; }

    public long getSinStock() { return sinStock; }
    public void setSinStock(long sinStock) { this.sinStock = sinStock; }

    public double getPorcentaje() { return porcentaje; }
    public void setPorcentaje(double porcentaje) { this.porcentaje = porcentaje; }
}
