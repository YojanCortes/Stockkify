package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface MovimientoRepository extends JpaRepository<Movimiento, Long> { }
