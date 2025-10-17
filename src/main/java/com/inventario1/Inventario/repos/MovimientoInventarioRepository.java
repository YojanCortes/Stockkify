package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.MovimientoInventario;
import com.inventario1.Inventario.models.TipoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {
    boolean existsByReferencia(String referencia);
    Optional<MovimientoInventario> findByReferencia(String referencia);
    List<MovimientoInventario> findAllByFechaBetween(LocalDateTime desde, LocalDateTime hasta);
    List<MovimientoInventario> findAllByTipo(TipoMovimiento tipo);
    long countByTipoAndFechaBetween(TipoMovimiento tipo, LocalDateTime desde, LocalDateTime hasta);
    void deleteByReferencia(String referencia);
}
