// Ubicación: src/main/java/com/inventario1/Inventario/repos/ProductoRepository.java
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

    // ==== LISTADOS BÁSICOS ====
    Page<Producto> findByActivoTrue(Pageable pageable);    // /buscar (solo activos)
    Page<Producto> findByActivoFalse(Pageable pageable);   // /buscar-inactivo (solo inactivos)
    List<Producto> findByActivoTrue();                     // <— para Alertas: SOLO activos sin paginar

    // ==== BÚSQUEDA PAGINADA ====
    @Query("select p.codigoBarras from Producto p where p.codigoBarras in :cbs")
    List<String> findExistingCodigos(@Param("cbs") Collection<String> cbs);

    @Query("""
           select p
           from Producto p
           where p.activo = true and
                 (lower(p.nombre) like lower(concat('%', :nombre, '%'))
                  or p.codigoBarras like concat('%', :codigo, '%'))
           """)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    Page<Producto> findByNombreContainingIgnoreCaseOrCodigoBarrasContaining(
            @Param("nombre") String nombre,
            @Param("codigo") String codigo,
            Pageable pageable
    );

    @Query(
            value = """
            select p from Producto p
            where p.activo = true and
                  (:q is null or :q = ''
                   or lower(p.nombre) like lower(concat('%', :q, '%'))
                   or p.codigoBarras like concat('%', :q, '%'))
            order by p.codigoBarras
            """,
            countQuery = """
            select count(p) from Producto p
            where p.activo = true and
                  (:q is null or :q = ''
                   or lower(p.nombre) like lower(concat('%', :q, '%'))
                   or p.codigoBarras like concat('%', :q, '%'))
            """
    )
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    Page<Producto> search(@Param("q") String q, Pageable pageable);

    // ==== BÚSQUEDA PAGINADA (INACTIVOS) ====
    @Query(
            value = """
            select p from Producto p
            where p.activo = false and
                  (:q is null or :q = ''
                   or lower(p.nombre) like lower(concat('%', :q, '%'))
                   or p.codigoBarras like concat('%', :q, '%'))
            order by p.codigoBarras
            """,
            countQuery = """
            select count(p) from Producto p
            where p.activo = false and
                  (:q is null or :q = ''
                   or lower(p.nombre) like lower(concat('%', :q, '%'))
                   or p.codigoBarras like concat('%', :q, '%'))
            """
    )
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    Page<Producto> searchInactivos(@Param("q") String q, Pageable pageable);

    // ==== BÚSQUEDA SIN PAGINAR ====
    @Query("""
        select p from Producto p
        where p.activo = true and
              (:q is null or :q = ''
               or lower(p.nombre) like lower(concat('%', :q, '%'))
               or p.codigoBarras like concat('%', :q, '%'))
        """)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> search(@Param("q") String q);

    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> findTop200ByActivoTrueOrderByCodigoBarrasAsc();

    // ==== CRÍTICOS (SOLO ACTIVOS) ====
    @Query("""
        select p from Producto p
        where p.activo = true
          and p.stockActual <= coalesce(p.stockMinimo, 0)
        order by p.stockActual asc
        """)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> findCriticos();

    @Query("""
        select p from Producto p
        where p.activo = true
          and p.stockActual <= coalesce(p.stockMinimo, 0)
        """)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    Page<Producto> findCriticos(Pageable pageable);

    @Query("""
        select count(p) from Producto p
        where p.activo = true
          and p.stockActual <= coalesce(p.stockMinimo, 0)
        """)
    long countCriticos();

    // ==== UTILIDADES ====
    Optional<Producto> findByCodigoBarrasAndActivoTrue(String codigoBarras);
    boolean existsByCodigoBarrasAndActivoTrue(String codigoBarras);

    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Producto> findAllByCodigoBarrasIn(Collection<String> codigos);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.codigoBarras = :codigo")
    Optional<Producto> lockByCodigo(@Param("codigo") String codigoBarras);

    // ==== SOFT DELETE / REACTIVAR ====
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Producto p set p.activo = false, p.actualizadoEn = current_timestamp where p.id = :id")
    int marcarInactivo(@Param("id") Long id);

    @Deprecated
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Producto p set p.activo = false, p.actualizadoEn = current_timestamp where p.id = :id")
    int desactivarPorId(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Producto p set p.activo = false, p.actualizadoEn = current_timestamp where p.codigoBarras = :codigo")
    int desactivarPorCodigo(@Param("codigo") String codigoBarras);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Producto p set p.activo = true, p.actualizadoEn = current_timestamp where p.codigoBarras = :codigo")
    int reactivarPorCodigo(@Param("codigo") String codigoBarras);

    @Override
    @Transactional
    void deleteById(Long id);
}
