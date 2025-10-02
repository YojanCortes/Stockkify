package com.inventario1.Inventario.services.dto;


import com.inventario1.Inventario.models.Producto;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

public class BulkProductosForm {
    @Valid
    private List<Producto> items = new ArrayList<>();

    public List<Producto> getItems() { return items; }
    public void setItems(List<Producto> items) { this.items = items; }

}
