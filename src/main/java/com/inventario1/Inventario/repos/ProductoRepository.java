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

    // ==== API AUTOCOMPLETAR ====
    Optional<Producto> findByCodigoBarras(String codigoBarras);
    boolean existsByCodigoBarras(String codigoBarras);

    // ==== BÚSQUEDA PAGINADA ====
    @Query("select p.codigoBarras from Producto p where p.codigoBarras in :cbs")
    List<String> findExistingCodigos(@Param("cbs") Collection<String> cbs);

    Page<Producto> findByNombreContainingIgnoreCaseOrCodigoBarrasContaining(
            String nombre, String codigo, Pageable pageable
    );

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

    // ==== BÚSQUEDA SIN PAGINAR ====
    @Query("""
        SELECT p FROM Producto p
        WHERE p.activo = true AND
              (:q IS NULL OR :q = ''
               OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
               OR p.codigoBarras LIKE CONCAT('%', :q, '%'))
        """)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> search(@Param("q") String q);

    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> findTop200ByActivoTrueOrderByCodigoBarrasAsc();

    // ==== CRÍTICOS ====
    @Query("""
        SELECT p FROM Producto p
        WHERE p.activo = true
          AND p.stockActual <= COALESCE(p.stockMinimo, 0)
        ORDER BY p.stockActual ASC
        """)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> findCriticos();

    @Query("""
        SELECT p FROM Producto p
        WHERE p.activo = true
          AND p.stockActual <= COALESCE(p.stockMinimo, 0)
        """)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    Page<Producto> findCriticos(Pageable pageable);

    @Query("""
        SELECT COUNT(p) FROM Producto p
        WHERE p.activo = true
          AND p.stockActual <= COALESCE(p.stockMinimo, 0)
        """)
    long countCriticos();

    // ==== UTILIDADES ====
    Optional<Producto> findByCodigoBarrasAndActivoTrue(String codigoBarras);
    boolean existsByCodigoBarrasAndActivoTrue(String codigoBarras);

    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> findAllByCodigoBarrasIn(Collection<String> codigos);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.codigoBarras = :codigo")
    Optional<Producto> lockByCodigo(@Param("codigo") String codigoBarras);

    // ==== SOFT DELETE / REACTIVAR ====
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.activo = false, p.actualizadoEn = CURRENT_TIMESTAMP WHERE p.id = :id")
    int marcarInactivo(@Param("id") Long id);

    @Deprecated
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.activo = false, p.actualizadoEn = CURRENT_TIMESTAMP WHERE p.id = :id")
    int desactivarPorId(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.activo = false, p.actualizadoEn = CURRENT_TIMESTAMP WHERE p.codigoBarras = :codigo")
    int desactivarPorCodigo(@Param("codigo") String codigoBarras);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Producto p SET p.activo = true, p.actualizadoEn = CURRENT_TIMESTAMP WHERE p.codigoBarras = :codigo")
    int reactivarPorCodigo(@Param("codigo") String codigoBarras);

    @Override
    @Transactional
    void deleteById(Long id);
}
