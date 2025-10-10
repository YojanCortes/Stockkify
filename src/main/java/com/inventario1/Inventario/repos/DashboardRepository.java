package com.inventario1.Inventario.repos;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.inventario1.Inventario.models.Producto;

public interface DashboardRepository extends Repository<Producto, Long> {

    /* ================== KPI 3 ================== */
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


    /* ================== KPI 4 ================== */
    @Query(value = """
        SELECT
            COALESCE(SUM(COALESCE(p.precio, 0) * GREATEST(COALESCE(p.stock_actual,0), 0)), 0)
        FROM productos p
        WHERE p.activo = 1
        """, nativeQuery = true)
    BigDecimal valorTotalInventarioCLP();

    @Query(value = """
        SELECT COALESCE(SUM(GREATEST(COALESCE(p.stock_actual,0), 0)), 0)
        FROM productos p
        WHERE p.activo = 1
        """, nativeQuery = true)
    Long totalItemsInventario();

    @Query(value = """
        SELECT
            COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL') AS categoria,
            COALESCE(SUM(COALESCE(p.precio, 0) * GREATEST(COALESCE(p.stock_actual,0), 0)), 0) AS valor
        FROM productos p
        WHERE p.activo = 1
        GROUP BY COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL')
        ORDER BY valor DESC
        """, nativeQuery = true)
    List<Object[]> valorInventarioPorCategoria();


    /* ================== STOCK DE SEGURIDAD (insumos históricos) ================== */
    @Query(value = """
    WITH v AS (
      SELECT p.id,
             p.codigo_barras,
             p.nombre,
             COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL') AS categoria,
             COALESCE(p.stock_actual,0) AS stock_actual
      FROM productos p
      WHERE p.activo = 1
    ),
    d AS (
      SELECT ml.producto_id AS id,
             DATE(mi.fecha)  AS dia,
             SUM(ml.cantidad) AS qty
      FROM movimiento_lineas ml
      JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
      WHERE mi.tipo = 'SALIDA'
        AND DATE(mi.fecha) BETWEEN :desde AND :hasta
      GROUP BY ml.producto_id, DATE(mi.fecha)
    )
    SELECT v.id, v.codigo_barras, v.nombre, v.categoria, v.stock_actual,
           COALESCE(SUM(d.qty),0)            AS total,
           COALESCE(SUM(d.qty * d.qty),0)    AS sumsq
    FROM v
    LEFT JOIN d ON d.id = v.id
    WHERE (:categoria IS NULL OR LOWER(v.categoria) = LOWER(:categoria))
    GROUP BY v.id, v.codigo_barras, v.nombre, v.categoria, v.stock_actual
    """, nativeQuery = true)
    List<Object[]> demandaHistoricaTotales(@Param("desde") LocalDate desde,
                                           @Param("hasta") LocalDate hasta,
                                           @Param("categoria") String categoria);


    /* ================== KPI 7 ================== */
    @Query(value = """
        SELECT
            p.codigo_barras  AS codigoBarras,
            p.nombre         AS nombre,
            p.categoria      AS categoria,
            SUM(ml.cantidad) AS unidades
        FROM movimiento_lineas ml
        JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
        JOIN productos p                ON p.id = ml.producto_id
        WHERE mi.tipo = 'SALIDA'
          AND mi.fecha BETWEEN :desde AND :hasta
        GROUP BY p.codigo_barras, p.nombre, p.categoria
        ORDER BY unidades DESC
        LIMIT 5
        """, nativeQuery = true)
    List<Object[]> top5VendidosEntre(@Param("desde") LocalDateTime desde,
                                     @Param("hasta") LocalDateTime hasta);


    /* ================== KPI 8 ================== */
    @Query(value = """
        SELECT
            YEARWEEK(mi.fecha, 3) AS anioSemana,
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

    @Query(value = """
        SELECT COALESCE(SUM(ml.cantidad * COALESCE(p.precio,0)), 0)
        FROM movimiento_lineas ml
        JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
        JOIN productos p ON p.id = ml.producto_id
        WHERE mi.tipo = 'SALIDA'
          AND mi.fecha BETWEEN :desde AND :hasta
        """, nativeQuery = true)
    BigDecimal ventasNetasEntre(@Param("desde") LocalDateTime desde,
                                @Param("hasta") LocalDateTime hasta);


    /* ================== KPI 10 — Rentabilidad (base) ================== */
    @Query(value = """
    WITH ult AS (
      SELECT
        ml.producto_id                              AS producto_id,
        COALESCE(ml.costo_unitario, 0)             AS costo_unitario,
        ROW_NUMBER() OVER (
          PARTITION BY ml.producto_id
          ORDER BY mi.fecha DESC, ml.id DESC
        ) AS rn
      FROM movimiento_lineas ml
      JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
      WHERE mi.tipo = 'ENTRADA'
    )
    SELECT
      p.codigo_barras,
      p.nombre,
      COALESCE(p.precio, 0)         AS precio_venta,
      COALESCE(u.costo_unitario, 0) AS costo_unitario
    FROM productos p
    LEFT JOIN ult u ON u.producto_id = p.id AND u.rn = 1
    WHERE p.activo = 1
    """, nativeQuery = true)
    List<Object[]> rentabilidadBasica();

    /* Alias compatibilidad: lista solo con precio (sin costo) */
    @Query(value = """
        SELECT
          p.codigo_barras,
          p.nombre,
          COALESCE(p.precio, 0) AS precio_venta
        FROM productos p
        WHERE p.activo = 1
        """, nativeQuery = true)
    List<Object[]> productosSoloPrecio();

    /* Alias compatibilidad: precio + costo (idéntico a rentabilidadBasica) */
    @Query(value = """
    WITH ult AS (
      SELECT
        ml.producto_id                              AS producto_id,
        COALESCE(ml.costo_unitario, 0)             AS costo_unitario,
        ROW_NUMBER() OVER (
          PARTITION BY ml.producto_id
          ORDER BY mi.fecha DESC, ml.id DESC
        ) AS rn
      FROM movimiento_lineas ml
      JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
      WHERE mi.tipo = 'ENTRADA'
    )
    SELECT
      p.codigo_barras,
      p.nombre,
      COALESCE(p.precio, 0)         AS precio_venta,
      COALESCE(u.costo_unitario, 0) AS costo_unitario
    FROM productos p
    LEFT JOIN ult u ON u.producto_id = p.id AND u.rn = 1
    WHERE p.activo = 1
    """, nativeQuery = true)
    List<Object[]> productosPrecioYCosto();


    /* ============ KPI 6 — Stock Obsoleto (> X días sin venta) ============ */
    @Query(value = """
    WITH base AS (
      SELECT p.id,
             COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL') AS categoria,
             COALESCE(p.stock_actual,0)                         AS stock,
             MAX(CASE WHEN mi.tipo='SALIDA' THEN DATE(mi.fecha) END) AS ultima_venta
      FROM productos p
      LEFT JOIN movimiento_lineas ml ON ml.producto_id = p.id
      LEFT JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
      WHERE p.activo = 1
      GROUP BY p.id
    )
    SELECT COUNT(*)
    FROM base
    WHERE stock > 0
      AND (ultima_venta IS NULL OR DATEDIFF(CURDATE(), ultima_venta) > :dias)
    """, nativeQuery = true)
    long countObsoletos(@Param("dias") int dias);

    @Query(value = """
    WITH base AS (
      SELECT p.id,
             COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL') AS categoria,
             COALESCE(p.stock_actual,0)                         AS stock,
             MAX(CASE WHEN mi.tipo='SALIDA' THEN DATE(mi.fecha) END) AS ultima_venta
      FROM productos p
      LEFT JOIN movimiento_lineas ml ON ml.producto_id = p.id
      LEFT JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
      WHERE p.activo = 1
      GROUP BY p.id
    )
    SELECT categoria, COUNT(*) AS cnt
    FROM base
    WHERE stock > 0
      AND (ultima_venta IS NULL OR DATEDIFF(CURDATE(), ultima_venta) > :dias)
    GROUP BY categoria
    ORDER BY cnt DESC
    """, nativeQuery = true)
    List<Object[]> obsoletoConteoPorCategoria(@Param("dias") int dias);

    /* === KPI 6 — Detalle paginado + búsqueda === */
    @Query(value = """
    WITH base AS (
      SELECT
        p.id,
        p.codigo_barras                   AS codigo,
        p.nombre                          AS nombre,
        COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL') AS categoria,
        COALESCE(p.stock_actual,0)        AS stock,
        MAX(CASE WHEN mi.tipo='SALIDA' THEN DATE(mi.fecha) END) AS ultima_venta
      FROM productos p
      LEFT JOIN movimiento_lineas ml ON ml.producto_id = p.id
      LEFT JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
      WHERE p.activo = 1
      GROUP BY p.id
    )
    SELECT COUNT(*)
    FROM base b
    WHERE b.stock > 0
      AND (b.ultima_venta IS NULL OR DATEDIFF(CURDATE(), b.ultima_venta) > :dias)
      AND (:q IS NULL
           OR LOWER(b.codigo) LIKE CONCAT('%', :q, '%')
           OR LOWER(b.nombre) LIKE CONCAT('%', :q, '%'))
    """, nativeQuery = true)
    long obsoletoCountFiltro(@Param("dias") int dias,
                             @Param("q") String q);

    @Query(value = """
    WITH base AS (
      SELECT
        p.id,
        p.codigo_barras                   AS codigo,
        p.nombre                          AS nombre,
        COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL') AS categoria,
        COALESCE(p.stock_actual,0)        AS stock,
        MAX(CASE WHEN mi.tipo='SALIDA' THEN DATE(mi.fecha) END) AS ultima_venta
      FROM productos p
      LEFT JOIN movimiento_lineas ml ON ml.producto_id = p.id
      LEFT JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
      WHERE p.activo = 1
      GROUP BY p.id
    )
    SELECT
      b.codigo            AS codigoBarras,
      b.nombre            AS nombre,
      b.categoria         AS categoria,
      b.stock             AS stock,
      CASE WHEN b.ultima_venta IS NULL THEN 9999
           ELSE DATEDIFF(CURDATE(), b.ultima_venta) END AS diasSinVenta
    FROM base b
    WHERE b.stock > 0
      AND (b.ultima_venta IS NULL OR DATEDIFF(CURDATE(), b.ultima_venta) > :dias)
      AND (:q IS NULL
           OR LOWER(b.codigo) LIKE CONCAT('%', :q, '%')
           OR LOWER(b.nombre) LIKE CONCAT('%', :q, '%'))
    ORDER BY diasSinVenta DESC, b.nombre ASC
    LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
    List<Object[]> obsoletoPage(@Param("dias") int dias,
                                @Param("q") String q,
                                @Param("limit") int limit,
                                @Param("offset") int offset);


    /* ============ Baja Rotación ============ */
    @Query(value = """
        WITH v AS (
          SELECT p.id,
                 COALESCE(SUM(CASE
                     WHEN mi.tipo='SALIDA' AND DATE(mi.fecha) BETWEEN :desde AND :hasta
                     THEN ml.cantidad ELSE 0 END),0) AS vtas_rango,
                 MAX(CASE WHEN mi.tipo='SALIDA' AND DATE(mi.fecha) <= :hasta THEN DATE(mi.fecha) END) AS ultima_venta
          FROM productos p
          LEFT JOIN movimiento_lineas ml ON ml.producto_id = p.id
          LEFT JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
          GROUP BY p.id
        )
        SELECT COUNT(*)
        FROM productos p
        JOIN v ON v.id = p.id
        WHERE ((v.vtas_rango / :diasRango) * 30) <= :umbral
          AND (:categoria IS NULL OR LOWER(COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL')) = LOWER(:categoria))
        """, nativeQuery = true)
    long countBajaRotacion(@Param("umbral") int umbral,
                           @Param("desde") LocalDate desde,
                           @Param("hasta") LocalDate hasta,
                           @Param("diasRango") int diasRango,
                           @Param("categoria") String categoria);

    @Query(value = """
        WITH v AS (
          SELECT p.id,
                 COALESCE(SUM(CASE
                     WHEN mi.tipo='SALIDA' AND DATE(mi.fecha) BETWEEN :desde AND :hasta
                     THEN ml.cantidad ELSE 0 END),0) AS vtas_rango,
                 MAX(CASE WHEN mi.tipo='SALIDA' AND DATE(mi.fecha) <= :hasta THEN DATE(mi.fecha) END) AS ultima_venta
          FROM productos p
          LEFT JOIN movimiento_lineas ml ON ml.producto_id = p.id
          LEFT JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
          GROUP BY p.id
        )
        SELECT
          p.nombre                                                      AS nombre,
          COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL')           AS categoria,
          ROUND((v.vtas_rango / :diasRango) * 30, 2)                   AS ventasMensuales,
          COALESCE(p.stock_actual, 0)                                  AS stock,
          CASE WHEN v.ultima_venta IS NULL THEN 9999
               ELSE GREATEST(0, DATEDIFF(:hasta, v.ultima_venta)) END  AS diasSinVenta,
          (GREATEST(COALESCE(p.stock_actual,0),0)
             * COALESCE(p.precio, 0))                                  AS costoTotal
        FROM productos p
        JOIN v ON v.id = p.id
        WHERE ((v.vtas_rango / :diasRango) * 30) <= :umbral
          AND (:categoria IS NULL OR LOWER(COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL')) = LOWER(:categoria))
        ORDER BY ventasMensuales ASC, diasSinVenta DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<Object[]> bajaRotacionPage(@Param("umbral") int umbral,
                                    @Param("desde") LocalDate desde,
                                    @Param("hasta") LocalDate hasta,
                                    @Param("diasRango") int diasRango,
                                    @Param("categoria") String categoria,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    @Query(value = """
        WITH v AS (
          SELECT p.id,
                 COALESCE(SUM(CASE
                     WHEN mi.tipo='SALIDA' AND DATE(mi.fecha) BETWEEN :desde AND :hasta
                     THEN ml.cantidad ELSE 0 END),0) AS vtas_rango
          FROM productos p
          LEFT JOIN movimiento_lineas ml ON ml.producto_id = p.id
          LEFT JOIN movimientos_inventario mi ON mi.id = ml.movimiento_id
          GROUP BY p.id
        )
        SELECT COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL') AS categoria,
               COUNT(*) AS cnt
        FROM productos p
        JOIN v ON v.id = p.id
        WHERE ((v.vtas_rango / :diasRango) * 30) <= :umbral
          AND (:categoria IS NULL OR LOWER(COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL')) = LOWER(:categoria))
        GROUP BY COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL')
        ORDER BY cnt DESC
        """, nativeQuery = true)
    List<Object[]> bajaRotacionConteoPorCategoria(@Param("umbral") int umbral,
                                                  @Param("desde") LocalDate desde,
                                                  @Param("hasta") LocalDate hasta,
                                                  @Param("diasRango") int diasRango,
                                                  @Param("categoria") String categoria);

    /* ===== Catálogo simple de categorías ===== */
    @Query(value = """
        SELECT DISTINCT COALESCE(NULLIF(TRIM(p.categoria), ''), 'GENERAL') AS nombre
        FROM productos p
        WHERE p.activo = 1
        ORDER BY nombre ASC
        """, nativeQuery = true)
    List<Object[]> catalogoCategoriasSimple();


    /* ================== Util (detección de columnas de costo) ================== */
    @Query(value = """
        SELECT IFNULL((
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name   = 'productos'
            AND column_name IN ('costo','costo_unitario','precio_compra')
          LIMIT 1
        ), 0)
        """, nativeQuery = true)
    int hasColCostoProductos();

    @Query(value = """
        SELECT IFNULL((
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name   = 'movimiento_lineas'
            AND column_name IN ('costo_unitario','precio_compra')
          LIMIT 1
        ), 0)
        """, nativeQuery = true)
    int hasColCostoEnMovLines();


    /* ================== Util ================== */
    default boolean ping() { return true; }
}
