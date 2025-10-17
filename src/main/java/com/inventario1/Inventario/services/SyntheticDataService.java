// path: src/main/java/com/inventario1/Inventario/services/SyntheticDataService.java
package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.Categoria;
import com.inventario1.Inventario.models.UnidadBase;
import com.inventario1.Inventario.services.dto.FilaProducto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generador de datos sintéticos coherentes con el importador de productos.
 * Por qué: poblar rápido para pruebas end-to-end sin romper enums/validaciones.
 */
@Service
@RequiredArgsConstructor
public class SyntheticDataService {

    /**
     * Genera N filas listas para el importador de productos.
     *
     * @param n          cantidad de productos
     * @param withStock  si true, fija stockMinimo > 0 y activa flags
     * @param fechaMin   no usado aquí (preservado por compatibilidad)
     * @param rangoDias  no usado aquí (preservado por compatibilidad)
     * @param seed       semilla opcional para reproducibilidad
     */
    public List<FilaProducto> generarProductos(
            int n,
            boolean withStock,
            LocalDate fechaMin,
            int rangoDias,
            Long seed
    ) {
        final Random rnd = (seed == null) ? new Random() : new Random(seed);
        final ThreadLocalRandom tlr = ThreadLocalRandom.current();
        final Set<String> usados = new HashSet<>();
        final List<FilaProducto> out = new ArrayList<>(n);

        final String[] marcas = {
                "Coca-Cola","Pepsi","CCU","Watts","Gato","Bacardi",
                "Lays","Evercrisp","Nestle","Colun","Unilever","Escudo","Cristal","Corona"
        };
        final String[] bases = {
                "Agua","Néctar Durazno","Coca-Cola","Pepsi","Papas Fritas",
                "Maní Salado","Vino Gato Blanco","Ron Bacardi","Cerveza Escudo","Cerveza Cristal","Cerveza Corona"
        };
        final String[] formas = {"Botella","Lata","Paquete","Caja","Frasco","Bolsa"};
        final String[] tamanios = {"250ml","330ml","350ml","500ml","1L","120g","100g","750ml","1kg"};

        for (int i = 0; i < n; i++) {
            String base = pick(rnd, bases);
            String forma = pick(rnd, formas);
            String tam   = pick(rnd, tamanios);
            String marca = pick(rnd, marcas);

            String nombre = base + " " + tam;

            // Categoría según base
            Categoria categoria = inferCategoria(base);

            // Unidad base
            UnidadBase unidad = UnidadBase.UNIDAD;

            // Volumen nominal (ml) si aplica
            Integer volumenMl = parseVolumenMl(tam);

            // Graduación para alcoholes
            Double grad = null;
            if (categoria == Categoria.CERVEZAS) grad = tlr.nextDouble(4.0, 7.0);
            else if (categoria == Categoria.VINOS) grad = tlr.nextDouble(11.0, 14.5);
            else if (categoria == Categoria.LICORES) grad = tlr.nextDouble(30.0, 45.0);

            // Precio (entero) coherente con categoría/volumen
            int precio = precioSugerido(categoria, volumenMl, rnd);

            // Flags
            Boolean perecible = (categoria == Categoria.ALIMENTOS || categoria == Categoria.JUGOS);
            Boolean retornable = (categoria == Categoria.CERVEZAS || categoria == Categoria.BEBIDAS) && rnd.nextBoolean();
            Integer stockMinimo = withStock ? rnd.nextInt(1, 10) : null;
            Boolean activo = Boolean.TRUE;

            // Código de barras 13 dígitos (no checksum real, solo único)
            String codigo;
            do {
                long num = Math.abs(rnd.nextLong()) % 1_000_000_000_000L; // 12 dígitos
                codigo = String.format("%013d", num);
            } while (!usados.add(codigo));

            // Construcción de fila
            FilaProducto fila = FilaProducto.builder()
                    .linea(i + 2L) // simula línea en CSV (1 = header)
                    .codigoBarras(codigo)
                    .nombre(nombre)
                    .marca(marca)
                    .precio(precio)
                    .categoria(categoria)
                    .unidadBase(unidad)
                    .volumenNominalMl(volumenMl)
                    .graduacionAlcoholica(grad)
                    .perecible(perecible)
                    .retornable(retornable)
                    .stockMinimo(stockMinimo)
                    .activo(activo)
                    .build();

            out.add(fila);
        }

        return out;
    }

    // ---------- helpers ----------

    private static <T> T pick(Random rnd, T[] arr) {
        return arr[rnd.nextInt(arr.length)];
    }

    private static Integer parseVolumenMl(String tam) {
        String t = tam.trim().toLowerCase(Locale.ROOT);
        if (t.endsWith("ml")) {
            try { return Integer.parseInt(t.replace("ml","").trim()); } catch (Exception ignored) {}
        }
        if (t.endsWith("l") && !t.endsWith("ml")) {
            try {
                double l = Double.parseDouble(t.substring(0, t.length()-1).trim());
                return (int) Math.round(l * 1000.0);
            } catch (Exception ignored) {}
        }
        // gramos/kilos u otros => null
        return null;
    }

    private static Categoria inferCategoria(String base) {
        String b = base.toLowerCase(Locale.ROOT);
        if (b.contains("cerveza")) return Categoria.CERVEZAS;
        if (b.contains("ron") || b.contains("whisky") || b.contains("pisco")) return Categoria.LICORES;
        if (b.contains("vino")) return Categoria.VINOS;
        if (b.contains("coca-cola") || b.contains("pepsi") || b.contains("agua")) return Categoria.BEBIDAS;
        if (b.contains("néctar") || b.contains("nectar")) return Categoria.JUGOS;
        if (b.contains("maní") || b.contains("mani") || b.contains("papas") || b.contains("galletas")) return Categoria.SNACKS;
        return Categoria.ALIMENTOS;
    }

    private static int precioSugerido(Categoria cat, Integer volumenMl, Random rnd) {
        int base = switch (cat) {
            case CERVEZAS -> 1500;
            case VINOS -> 3500;
            case LICORES -> 9000;
            case BEBIDAS -> 1200;
            case JUGOS -> 1000;
            case SNACKS -> 800;
            case AGUAS -> 700;
            default -> 1000;
        };
        int factorVol = (volumenMl != null) ? Math.max(0, (volumenMl - 250)) / 100 : 0;
        int ruido = rnd.nextInt(0, 401); // 0..400
        return Math.max(300, base + factorVol * 50 + ruido);
    }
}
