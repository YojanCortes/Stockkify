// path: src/main/java/com/inventario1/Inventario/models/UnidadBase.java
package com.inventario1.Inventario.models;

import java.util.Locale;
import java.util.Map;

/**
 * Enum canónico. Mantén aquí SOLO los valores "oficiales" que quieres en BD.
 */
public enum UnidadBase {
    UNIDAD,
    PORCION,
    PACK,
    CAJA,
    KEG,
    ML,     // si los necesitas realmente
    LITRO;  // ídem

    // Alias que llegaron a BD con distintos nombres => se normalizan aquí.
    private static final Map<String, UnidadBase> ALIASES = Map.ofEntries(
            // claves en minúsculas, sin tildes, sin espacios
            Map.entry("unidad", UNIDAD),
            Map.entry("unidades", UNIDAD),
            Map.entry("u", UNIDAD),
            Map.entry("porcion", PORCION),
            Map.entry("porcion(es)", PORCION),
            Map.entry("pack", PACK),
            Map.entry("caja", CAJA),
            Map.entry("keg", KEG),
            Map.entry("ml", ML),
            Map.entry("mililitro", ML),
            Map.entry("mililitros", ML),
            Map.entry("l", LITRO),
            Map.entry("lt", LITRO),
            Map.entry("litro", LITRO),
            Map.entry("litros", LITRO)
    );

    /**
     * Normaliza texto arbitrario desde BD/API a una constante enum.
     * Lanza IllegalArgumentException si no hay mapeo (para detectar datos realmente corruptos).
     * Comentario "por qué": mantenemos validación fuerte pero aceptamos alias comunes.
     */
    public static UnidadBase fromDbValue(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("unidad_base nulo");
        }
        String key = normalize(raw);
        UnidadBase mapped = ALIASES.get(key);
        if (mapped != null) return mapped;

        // Como fallback, intenta mach con nombre exacto del enum
        try {
            return UnidadBase.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Valor unidad_base inválido: '" + raw + "'");
        }
    }

    /** Valor canónico para persistir en BD (ENUM/String). */
    public String toDbValue() {
        return this.name();
    }

    private static String normalize(String s) {
        String out = s.trim().toLowerCase(Locale.ROOT);
        // quita espacios y paréntesis básicos
        out = out.replace("(", "").replace(")", "");
        out = out.replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u");
        out = out.replaceAll("\\s+", "");
        return out;
    }
}
