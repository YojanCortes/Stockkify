package com.inventario1.Inventario.repos;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;       // interfaz de Spring Data
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// === IMPORTA TU ENTIDAD REAL ===
// Ajusta este import al paquete donde esté tu Producto.
import com.inventario1.Inventario.models.Producto;
// Si en tu proyecto es otro paquete, por ejemplo:
// import com.inventario1.Inventario.model.Producto;
// import com.inventario1.Inventario.entities.Producto;

public interface DashboardRepository extends Repository<Producto, Long> {

    /* ==============================
       KPI 3 — Rupturas de Stock (%)
       ============================== */

    @Query(value = """
        SELECT COUNT(*) 
        FROM productos p 
        WHERE p.activo = 1
        """, nativeQuery = true)
    long countProductosActivos();

    @Query(value = """
        SELECT COUNT(*) 
        FROM productos p 
        WHERE p.activo = 1
          AND (p.stock_actual IS NULL OR p.stock_actual <= 0)
        """, nativeQuery = true)
    long countProductosSinStock();

    @Query(value = """
        SELECT 
            p.id,
            p.codigo_barras,
            p.nombre,
            p.categoria,
            COALESCE(p.stock_actual, 0) AS stock_actual
        FROM productos p
        WHERE p.activo = 1
          AND (p.stock_actual IS NULL OR p.stock_actual <= 0)
        ORDER BY p.nombre ASC
        """, nativeQuery = true)
    List<Object[]> listarProductosSinStock();


    /* ============================================
       KPI 4 — Valor Total de Inventario (CLP)
       ============================================ */

    @Query(value = """
        SELECT COALESCE(SUM(COALESCE(p.precio_venta, p.precio, 0) * COALESCE(p.stock_actual, 0)), 0)
        FROM productos p
        WHERE p.activo = 1
        """, nativeQuery = true)
    BigDecimal valorTotalInventarioCLP();

    @Query(value = """
        SELECT p.categoria   AS categoria,
               COALESCE(SUM(COALESCE(p.precio_venta, p.precio, 0) * COALESCE(p.stock_actual, 0)), 0) AS valor
        FROM productos p
        WHERE p.activo = 1
        GROUP BY p.categoria
        ORDER BY valor DESC
        """, nativeQuery = true)
    List<Object[]> valorInventarioPorCategoria();


    /* =======================================
       KPI 7 — Top 5 Productos Más Vendidos
       ======================================= */

    @Query(value = """
        SELECT p.codigo_barras  AS codigoBarras,
               p.nombre         AS nombre,
               SUM(ml.cantidad) AS unidades
        FROM movimiento_lineas ml
        JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
        JOIN productos p               ON p.id = ml.producto_id
        WHERE mi.tipo = 'SALIDA'
          AND mi.fecha BETWEEN :desde AND :hasta
        GROUP BY p.codigo_barras, p.nombre
        ORDER BY unidades DESC
        LIMIT 5
        """, nativeQuery = true)
    List<Object[]> top5VendidosEntre(@Param("desde") LocalDateTime desde,
                                     @Param("hasta") LocalDateTime hasta);


    /* ==========================================
       KPI 8 — Consumo Estimado Semanal (base)
       ========================================== */
    @Query(value = """
        SELECT YEARWEEK(mi.fecha, 3) AS anioSemana,
               DATE_FORMAT(
                   STR_TO_DATE(CONCAT(YEARWEEK(mi.fecha, 3), ' Monday'), '%X%V %W'),
                   '%Y-%m-%d'
               ) AS semanaInicioAprox,
               SUM(ml.cantidad)      AS unidades
        FROM movimiento_lineas ml
        JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
        WHERE mi.tipo = 'SALIDA'
        GROUP BY YEARWEEK(mi.fecha, 3)
        ORDER BY anioSemana DESC
        LIMIT :semanas
        """, nativeQuery = true)
    List<Object[]> consumoSemanalUltimas(@Param("semanas") int semanas);

    // (opcional) para probar inyección rápidamente
    default boolean ping() { return true; }
}
