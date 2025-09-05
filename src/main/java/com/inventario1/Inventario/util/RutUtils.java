package com.inventario1.Inventario.util;

public final class RutUtils {
    private RutUtils() {}

    /** Quita puntos/guion/espacios y pasa K a mayúscula */
    public static String normalize(String s) {
        if (s == null) return null;
        String t = s.replaceAll("[\\.\\-\\s]", "").toUpperCase();
        return t.isBlank() ? null : t;
    }

    /** Acepta: (a) RUT con DV correcto o (b) sólo dígitos 4..12 (modo pruebas) */
    public static boolean isAcceptable(String raw) {
        String r = normalize(raw);
        if (r == null) return false;
        // Sólo dígitos (modo pruebas / históricos)
        if (r.matches("\\d{4,12}")) return true;
        // Con DV
        return isRutWithDvValid(r);
    }

    /** Valida RUT con DV (último caracter puede ser 0-9 o K) */
    public static boolean isRutWithDvValid(String r) {
        if (r == null || r.length() < 2) return false;
        String cuerpo = r.substring(0, r.length() - 1);
        char dv = r.charAt(r.length() - 1);
        if (!cuerpo.matches("\\d+")) return false;
        char esperado = dvMod11(cuerpo);
        return dv == esperado;
    }

    /** Calcula DV (método oficial chileno) */
    private static char dvMod11(String num) {
        int suma = 0, factor = 2;
        for (int i = num.length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(num.charAt(i)) * factor;
            factor = (factor == 7) ? 2 : factor + 1;
        }
        int resto = 11 - (suma % 11);
        return switch (resto) {
            case 11 -> '0';
            case 10 -> 'K';
            default -> (char) ('0' + resto);
        };
    }
}
