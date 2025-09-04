package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.Producto;
import jakarta.persistence.LockModeType;
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
     * Búsqueda paginada por nombre o código de barras (case-insensitive en nombre).
     * Si q es null o vacío, devuelve todo (paginado).
     */
    @Query("""
           SELECT p FROM Producto p
           WHERE (:q IS NULL OR :q = '')
              OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
              OR p.codigoBarras LIKE CONCAT('%', :q, '%')
           """)
    Page<Producto> search(@Param("q") String q, Pageable pageable);

    /**
     * Búsqueda sin paginar (útil para listados rápidos; ojo en tablas grandes).
     */
    @Query("""
           SELECT p FROM Producto p
           WHERE (:q IS NULL OR :q = '')
              OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
              OR p.codigoBarras LIKE CONCAT('%', :q, '%')
           """)
    List<Producto> search(@Param("q") String q);

    /**
     * Primeros 200, ordenados por código de barras ascendente.
     */
    List<Producto> findTop200ByOrderByCodigoBarrasAsc();


    /* =========================
       LOOKUP / UTILIDADES
       ========================= */

    Optional<Producto> findByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarras(String codigoBarras);

    /**
     * Carga en bloque por lista de códigos (usado por el servicio de movimientos).
     */
    List<Producto> findAllByCodigoBarrasIn(Collection<String> codigos);

    /**
     * (Opcional) Lock pesimista para lecturas-modificaciones explícitas.
     * No es necesario para movimientos porque el stock lo ajustan los triggers.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.codigoBarras = :codigo")
    Optional<Producto> lockByCodigo(@Param("codigo") String codigoBarras);


    /* =========================
       IMPORTANTE
       =========================
       Se eliminaron las operaciones atómicas de stock (descontar/incrementar)
       porque ahora el stock se gestiona en la BD con triggers.
       Mantén la lógica de entradas/salidas a través del servicio de movimientos.
     */
}
