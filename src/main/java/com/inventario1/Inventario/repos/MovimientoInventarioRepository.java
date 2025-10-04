package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {
    boolean existsByReferencia(String referencia);
    Optional<MovimientoInventario> findByReferencia(String referencia);
}
