package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.Producto;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, String> {

    List<Producto> findTop200ByOrderByCodigoBarrasAsc();

    @Query("""
           SELECT p FROM Producto p
           WHERE LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
              OR p.codigoBarras LIKE CONCAT('%', :q, '%')
           ORDER BY p.codigoBarras ASC
           """)
    List<Producto> search(@Param("q") String q);
}
