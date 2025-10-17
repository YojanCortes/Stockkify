// path: src/main/java/com/inventario1/Inventario/services/mapper/CsvProductoMapper.java
package com.inventario1.Inventario.services.mapper;

import com.inventario1.Inventario.models.Categoria;
import com.inventario1.Inventario.models.UnidadBase;
import com.inventario1.Inventario.services.dto.FilaProducto;
import com.inventario1.Inventario.util.csv.FlexibleCsvReader;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Mapea/valida una fila CSV a FilaProducto. */
public final class CsvProductoMapper {
    private CsvProductoMapper() {}

    public static FilaProducto fromRow(FlexibleCsvReader.CsvRow row) {
        List<String> errs = new ArrayList<>();

        String cb     = val(row, "codigo_barras");
        String nombre = val(row, "nombre");
        String marca  = val(row, "marca");
        String precio = val(row, "precio");
        String cat    = val(row, "categoria");
        String ub     = val(row, "unidad_base");

        String vol    = val(row, "volumen_nominal_ml");
        String grad   = val(row, "graduacion_alcoholica");
        String perec  = val(row, "perecible");
        String ret    = val(row, "retornable");
        String min    = val(row, "stock_minimo");
        String act    = val(row, "activo");

        if (isBlank(cb)) errs.add("codigo_barras vacío");
        if (isBlank(nombre)) errs.add("nombre vacío");
        if (isBlank(marca)) errs.add("marca vacío");

        Integer precioInt = parseMoneyAsInt(precio, errs, "precio");
        Integer volInt    = parseInt(vol, errs, "volumen_nominal_ml", false);
        Double gradD      = parseDouble(grad, errs, "graduacion_alcoholica", false);
        Boolean bPerec    = parseBool01(perec, errs, "perecible");
        Boolean bRet      = parseBool01(ret, errs, "retornable");
        Integer minInt    = parseInt(min, errs, "stock_minimo", false);
        Boolean bActivo   = parseBool01(act, errs, "activo");

        Categoria categoria = normalizeCategoria(cat);
        UnidadBase unidad   = normalizeUnidadBase(ub);
        if (categoria == null) errs.add("categoria inválida: " + cat);
        if (unidad == null) errs.add("unidad_base inválida: " + ub);

        String error = errs.isEmpty() ? null : String.join("; ", errs);

        return FilaProducto.builder()
                .linea(row.lineNumber)
                .codigoBarras(cb)
                .nombre(nombre)
                .marca(marca)
                .precio(precioInt)
                .categoria(categoria)
                .unidadBase(unidad)
                .volumenNominalMl(volInt)
                .graduacionAlcoholica(gradD)
                .perecible(bPerec)
                .retornable(bRet)
                .stockMinimo(minInt)
                .activo(bActivo)
                .error(error)
                .build();
    }

    // ----- helpers -----
    private static String val(FlexibleCsvReader.CsvRow row, String key){
        String v = row.values.get(key);
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
    private static boolean isBlank(String s){ return s==null || s.trim().isEmpty(); }

    private static Integer parseMoneyAsInt(String s, List<String> errs, String name){
        if (isBlank(s)) { errs.add(name + " vacío"); return null; }
        try {
            String norm = s.replaceAll("[\\s\\$]", "");
            if (norm.matches(".*\\,\\d{1,2}$") && norm.contains(".")) {
                norm = norm.replace(".", "").replace(",", ".");
            } else {
                norm = norm.replace(",", ".");
            }
            BigDecimal bd = new BigDecimal(norm);
            return bd.intValue(); // por qué: tu entidad usa int para precio
        } catch (Exception e) { errs.add(name + " inválido: " + s); return null; }
    }
    private static Integer parseInt(String s, List<String> errs, String name, boolean required){
        if (isBlank(s)) { if (required) errs.add(name + " vacío"); return null; }
        try { return Integer.valueOf(s.replaceAll("[^0-9-]","")); }
        catch (Exception e){ errs.add(name + " inválido: " + s); return null; }
    }
    private static Double parseDouble(String s, List<String> errs, String name, boolean required){
        if (isBlank(s)) { if (required) errs.add(name + " vacío"); return null; }
        try { return Double.valueOf(s.replace(",", ".")); }
        catch (Exception e){ errs.add(name + " inválido: " + s); return null; }
    }
    private static Boolean parseBool01(String s, List<String> errs, String name){
        if (isBlank(s)) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "1": case "true": case "si": case "sí": case "y": case "yes": return true;
            case "0": case "false": case "no": case "n": return false;
            default: errs.add(name + " debe ser 0/1/true/false"); return null;
        }
    }

    private static Categoria normalizeCategoria(String s){
        if (isBlank(s)) return null;
        String t = stripAccents(s).toUpperCase(Locale.ROOT).replaceAll("\\s+","");
        switch (t){
            case "GENERAL": return Categoria.GENERAL;
            case "ALIMENTOS": return Categoria.ALIMENTOS;
            case "INSUMOS": return Categoria.INSUMOS;
            case "BEBIDAS": return Categoria.BEBIDAS;
            case "CERVEZAS": return Categoria.CERVEZAS;
            case "LICORES": return Categoria.LICORES;
            case "VINOS": return Categoria.VINOS;
            case "AGUAS": return Categoria.AGUAS;
            case "SNACKS": return Categoria.SNACKS;
            case "ENERGETICA":
            case "ENERGETICAS": return Categoria.ENERGETICAS;
            case "JUGO":
            case "JUGOS": return Categoria.JUGOS;
            default: return null;
        }
    }
    private static UnidadBase normalizeUnidadBase(String s){
        if (isBlank(s)) return null;
        String t = stripAccents(s).toUpperCase(Locale.ROOT).replaceAll("\\s+","");
        switch (t){
            case "UNIDAD": case "U": case "UNIDADES": return UnidadBase.UNIDAD;
            case "PORCION": case "PORCIONES": return UnidadBase.PORCION;
            case "PACK": return UnidadBase.PACK;
            case "CAJA": return UnidadBase.CAJA;
            case "KEG": return UnidadBase.KEG;
            default: if (t.matches("\\d+")) return UnidadBase.UNIDAD; return null;
        }
    }
    private static String stripAccents(String s){
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+","");
    }
}
