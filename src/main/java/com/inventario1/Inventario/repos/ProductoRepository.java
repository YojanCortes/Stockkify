package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.Producto;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /* =========================
       BÚSQUEDA PAGINADA (NUEVA)
       ========================= */

    /**
     * Búsqueda derivada (Spring Data) para /buscar:
     * busca por nombre (contains, ignore case) o por código (contains).
     * Sin filtro de activo por compatibilidad.
     */
    Page<Producto> findByNombreContainingIgnoreCaseOrCodigoBarrasContaining(
            String nombre, String codigo, Pageable pageable
    );

    /**
     * Búsqueda paginada con parámetro único "q".
     * Filtra solo productos activos.
     */
    @Query(
            value = """
            SELECT p FROM Producto p
            WHERE p.activo = true AND
                  (:q IS NULL OR :q = ''
                   OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR p.codigoBarras LIKE CONCAT('%', :q, '%'))
            """,
            countQuery = """
            SELECT COUNT(p) FROM Producto p
            WHERE p.activo = true AND
                  (:q IS NULL OR :q = ''
                   OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR p.codigoBarras LIKE CONCAT('%', :q, '%'))
            """
    )
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    Page<Producto> search(@Param("q") String q, Pageable pageable);

    /* =========================
       BÚSQUEDA SIN PAGINAR
       ========================= */

    /**
     * Búsqueda sin paginar (ojo con tablas grandes).
     * Filtra solo activos. Si q es null/vacío, devuelve todo (activo).
     */
    @Query("""
        SELECT p FROM Producto p
        WHERE p.activo = true AND
              (:q IS NULL OR :q = ''
               OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
               OR p.codigoBarras LIKE CONCAT('%', :q, '%'))
        """)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> search(@Param("q") String q);

    /**
     * Primeros 200 activos, ordenados por código de barras ascendente.
     */
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> findTop200ByActivoTrueOrderByCodigoBarrasAsc();

    /* =========================
       LOOKUP / UTILIDADES
       ========================= */

    Optional<Producto> findByCodigoBarras(String codigoBarras);
    Optional<Producto> findByCodigoBarrasAndActivoTrue(String codigoBarras);

    boolean existsByCodigoBarras(String codigoBarras);
    boolean existsByCodigoBarrasAndActivoTrue(String codigoBarras);

    /**
     * Carga en bloque por lista de códigos.
     */
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> findAllByCodigoBarrasIn(Collection<String> codigos);

    /**
     * Lock pesimista para lecturas-modificaciones explícitas.
     * Requiere transacción activa en el servicio que la invoque.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.codigoBarras = :codigo")
    Optional<Producto> lockByCodigo(@Param("codigo") String codigoBarras);

    /* =========================
       SOFT DELETE / REACTIVAR
       ========================= */

    /**
     * Marca INACTIVO por ID (usado por el servicio para fallback de borrado).
     * Devuelve filas afectadas.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.activo = false, p.actualizadoEn = CURRENT_TIMESTAMP WHERE p.id = :id")
    int marcarInactivo(@Param("id") Long id);

    /**
     * (Mantención) Desactiva por ID.
     * Preferir usar {@link #marcarInactivo(Long)}.
     */
    @Deprecated
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.activo = false, p.actualizadoEn = CURRENT_TIMESTAMP WHERE p.id = :id")
    int desactivarPorId(@Param("id") Long id);

    /**
     * Desactiva por código de barras (útil en tareas batch).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.activo = false, p.actualizadoEn = CURRENT_TIMESTAMP WHERE p.codigoBarras = :codigo")
    int desactivarPorCodigo(@Param("codigo") String codigoBarras);

    /**
     * Reactiva por código de barras.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.activo = true, p.actualizadoEn = CURRENT_TIMESTAMP WHERE p.codigoBarras = :codigo")
    int reactivarPorCodigo(@Param("codigo") String codigoBarras);

    /**
     * deleteById ya existe en JpaRepository; lo anotamos transaccional
     * para dejar claro el contrato cuando se use directamente.
     */
    @Override
    @Transactional
    void deleteById(Long id);

    /* =========================
       NOTA
       =========================
       Las operaciones atómicas de stock deben manejarse en servicio
       (p. ej., mediante movimientos), no aquí en el repositorio.
     */
}
