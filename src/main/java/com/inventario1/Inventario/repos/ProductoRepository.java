package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, String> {

    /**
     * Busca productos cuyo nombre o código de barras contenga el texto dado (case-insensitive).
     * Usado por InventarioService.buscarProductos y BuscarController.
     */
    @Query("""
           SELECT p FROM Producto p
           WHERE LOWER(p.nombre)       LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(p.codigoBarras) LIKE LOWER(CONCAT('%', :q, '%'))
           """)
    Page<Producto> search(@Param("q") String q, Pageable pageable);

    /**
     * Versión sin paginación: devuelve lista completa (ojo con performance en tablas grandes).
     * Útil si quieres resultados rápidos en BuscarController.
     */
    @Query("""
           SELECT p FROM Producto p
           WHERE LOWER(p.nombre)       LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(p.codigoBarras) LIKE LOWER(CONCAT('%', :q, '%'))
           """)
    List<Producto> search(@Param("q") String q);

    /**
     * Devuelve los primeros 200 productos ordenados por código de barras ascendente.
     * Útil para la vista /buscar sin filtro.
     */
    List<Producto> findTop200ByOrderByCodigoBarrasAsc();

    /**
     * Descuenta stock de un producto de forma atómica.
     * Devuelve el número de filas actualizadas (0 si no se pudo).
     */
    @Modifying
    @Query("UPDATE Producto p SET p.cantidad = p.cantidad - :cant WHERE p.codigoBarras = :codigo AND p.cantidad >= :cant")
    int descontarStock(@Param("codigo") String codigoBarras, @Param("cant") int cantidad);
}
