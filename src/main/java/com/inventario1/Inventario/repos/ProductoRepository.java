package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.Producto;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /* =========================
       BÚSQUEDA
       ========================= */

    /**
     * Búsqueda paginada por nombre o código de barras.
     * Si q es null o vacío, devuelve todo (paginado).
     * Se agrega countQuery y readOnly para rendimiento.
     */
    @Query(
            value = """
                SELECT p FROM Producto p
                WHERE (:q IS NULL OR :q = ''
                       OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
                       OR p.codigoBarras LIKE CONCAT('%', :q, '%'))
                """,
            countQuery = """
                     SELECT COUNT(p) FROM Producto p
                     WHERE (:q IS NULL OR :q = ''
                            OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
                            OR p.codigoBarras LIKE CONCAT('%', :q, '%'))
                     """
    )
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    Page<Producto> search(@Param("q") String q, Pageable pageable);

    /**
     * Búsqueda sin paginar (ojo en tablas grandes).
     * Si q es null o vacío, devuelve todo.
     */
    @Query("""
           SELECT p FROM Producto p
           WHERE (:q IS NULL OR :q = ''
                  OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR p.codigoBarras LIKE CONCAT('%', :q, '%'))
           """)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> search(@Param("q") String q);

    /**
     * Primeros 200, ordenados por código de barras ascendente.
     */
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> findTop200ByOrderByCodigoBarrasAsc();

    /* =========================
       LOOKUP / UTILIDADES
       ========================= */

    Optional<Producto> findByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarras(String codigoBarras);

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
       IMPORTANTE
       =========================
       Se eliminaron operaciones atómicas de stock en el repositorio.
       Maneja el stock vía movimientos/servicio.
     */
}
