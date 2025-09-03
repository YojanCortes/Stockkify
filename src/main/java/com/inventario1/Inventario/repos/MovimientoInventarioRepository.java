package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> { }