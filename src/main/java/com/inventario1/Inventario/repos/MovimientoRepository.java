package com.inventario1.Inventario.repos;

// src/main/java/com/tuapp/inventario/repo/MovimientoRepository.java

import com.inventario1.Inventario.models.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {}
