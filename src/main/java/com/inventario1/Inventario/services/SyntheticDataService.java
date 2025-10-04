package com.inventario1.Inventario.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SyntheticDataService {

    public List<BulkProductoLoaderService.FilaProducto> generarProductos(
            int n,
            boolean withStock,
            LocalDate fechaMin,
            int rangoDias,
            Long seed // puede ser null
    ) {
        Random rnd = (seed == null) ? new Random() : new Random(seed);
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        Set<String> usados = new HashSet<>();

        String[] categorias = {"abarrotes","lacteos","limpieza","bebidas","snacks","aseo"};
        String[] formas = {"bolsa","botella","paquete","lata","frasco","caja"};
        String[] bases = {"arroz","leche","aceite","azucar","fideos","jabon","cafe","te","agua","galletas"};

        List<BulkProductoLoaderService.FilaProducto> out = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            String base = bases[rnd.nextInt(bases.length)];
            String forma = formas[rnd.nextInt(formas.length)];
            String tamanio = switch (rnd.nextInt(5)) {
                case 0 -> "250g";
                case 1 -> "500g";
                case 2 -> "1kg";
                case 3 -> "500ml";
                default -> "1L";
            };
            String nombre = base + " " + tamanio;

            String categoria = categorias[rnd.nextInt(categorias.length)];
            String presentacion = forma;

            // Código de barras sintético 13 dígitos (GTIN-13-like, sin checksum real)
            String codigo;
            do {
                codigo = String.format("%013d", Math.abs(rnd.nextLong()) % 1_000_000_000_000L);
            } while (!usados.add(codigo));

            // precios/costos sencillos
            BigDecimal costo = BigDecimal.valueOf( (rnd.nextInt(500) + 300) ); // 300..799
            BigDecimal precio = costo.add(BigDecimal.valueOf(rnd.nextInt(400) + 200)); // +200..+599

            Integer stockInicial = null;
            LocalDate fechaStock = null;
            if (withStock) {
                stockInicial = rnd.nextInt(201); // 0..200
                if (fechaMin == null) fechaMin = LocalDate.now().minusDays(30);
                int salto = rangoDias <= 0 ? 0 : rnd.nextInt(rangoDias+1);
                fechaStock = fechaMin.plusDays(salto);
            }

            BulkProductoLoaderService.FilaProducto fila = BulkProductoLoaderService.FilaProducto.builder()
                    .codigoBarras(codigo)
                    .nombreProducto(nombre)
                    .presentacion(presentacion)
                    .categoria(categoria)
                    .costoUnitario(costo)
                    .precioVenta(precio)
                    .stockInicial(stockInicial)          // puede ser null si withStock=false
                    .fechaStockInicial(fechaStock)       // puede ser null si withStock=false
                    .proveedor(null)                     // lo dejamos null a propósito
                    .activo(Boolean.TRUE)                // activos por defecto
                    .build();

            out.add(fila);
        }
        return out;
    }
}
