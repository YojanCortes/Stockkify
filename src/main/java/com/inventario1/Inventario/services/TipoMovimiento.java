package com.inventario1.Inventario.services;



import java.text.Normalizer;
import java.util.Locale;

/**
 * Mapea valores de archivo a ENTRADA/SALIDA. Acepta S/I literal.
 * Por qué: compatibilidad con planillas de ventas/stock simples.
 */
public enum TipoMovimiento {
    ENTRADA,
    SALIDA;

    /**
     * Convierte texto libre a TipoMovimiento:
     *  - "I", "ENTRADA", "IN"  -> ENTRADA
     *  - "S", "SALIDA", "OUT"  -> SALIDA
     *  - tolera espacios, acentos y minúsculas
     */
    public static TipoMovimiento from(String raw) {
        if (raw == null) return null;
        String t = normalize(raw);
        switch (t) {
            // ENTRADA
            case "I":
            case "IN":
            case "ENTRADA":
            case "E": // si en tu CSV usan 'E' como entrada
                return ENTRADA;
            // SALIDA
            case "S":
            case "OUT":
            case "SALIDA":
                return SALIDA;
            default:
                return null;
        }
    }

    private static String normalize(String s) {
        String x = s.trim();
        x = Normalizer.normalize(x, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+","");
        x = x.toUpperCase(Locale.ROOT);
        // compacta múltiples espacios
        x = x.replaceAll("\\s+", "");
        return x;
    }
}
