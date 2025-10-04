package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.MovimientoLinea;
import com.inventario1.Inventario.services.dto.MovimientoItemDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MovimientoLineaRepository extends JpaRepository<MovimientoLinea, Long> {

    @Query("""
           select new com.inventario1.Inventario.services.dto.MovimientoItemDTO(
                m.id,
                ml.id,
                p.codigoBarras,
                p.nombre,
                m.tipo,
                m.fecha,
                ml.cantidad
           )
           from MovimientoLinea ml
             join ml.movimiento m
             join ml.producto p
           order by m.fecha desc, m.id desc
           """)
    Page<MovimientoItemDTO> pageMovimientos(Pageable pageable);
}
