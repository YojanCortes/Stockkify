package com.inventario1.Inventario.services.dto;


import java.util.ArrayList;
import java.util.List;

public class BulkResult {
    public static class ItemError {
        public int index;
        public String codigoBarras;
        public String mensaje;
        public ItemError(int index, String cb, String msg){ this.index=index; this.codigoBarras=cb; this.mensaje=msg; }
    }
    private int inserted;
    private int updated;
    private int skipped;
    private List<ItemError> errors = new ArrayList<>();

    public void incInserted(){ inserted++; }
    public void incUpdated(){ updated++; }
    public void incSkipped(){ skipped++; }
    public void addError(int idx, String cb, String msg){ errors.add(new ItemError(idx, cb, msg)); }
    // getters
}

