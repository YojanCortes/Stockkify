package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.PersistenceException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductoService {

    private final ProductoRepository repo;
    private final PlatformTransactionManager txManager;

    public enum DeleteResult { BORRADO, INACTIVADO, NO_EXISTE }

    /**
     * Intenta borrar el producto por código de barras.
     * - Si no existe → NO_EXISTE
     * - Si se borra OK → BORRADO
     * - Si falla por FK → INACTIVADO (marca activo=false en una tx aparte)
     */
    public DeleteResult eliminarOInactivarPorCodigo(String codigoBarras) {
        Optional<Producto> opt = repo.findByCodigoBarras(codigoBarras);
        if (opt.isEmpty()) {
            log.info("Eliminar: producto no existe (cb={})", codigoBarras);
            return DeleteResult.NO_EXISTE;
        }

        Producto p = opt.get();
        var tx = new TransactionTemplate(txManager);

        // 1) Intentar borrar en su propia transacción
        try {
            tx.executeWithoutResult(status -> {
                log.info("Intentando borrar producto id={}, cb={}", p.getId(), p.getCodigoBarras());
                repo.delete(p);
                repo.flush(); // fuerza el DELETE para detectar FK aquí
            });
            log.info("Borrado OK id={}, cb={}", p.getId(), p.getCodigoBarras());
            return DeleteResult.BORRADO;

        } catch (DataIntegrityViolationException | PersistenceException ex) {
            log.warn("Borrado bloqueado (posible FK) cb={}: {}", codigoBarras, ex.getMessage());

            // 2) Fallback: marcar INACTIVO en otra transacción limpia
            tx.executeWithoutResult(status -> {
                int n = repo.marcarInactivo(p.getId());
                log.info("Producto marcado INACTIVO id={}, cb={}, filas={}", p.getId(), p.getCodigoBarras(), n);
            });
            return DeleteResult.INACTIVADO;
        }
    }

    /**
     * Reactiva un producto por código de barras (activo=true).
     * @return true si reactivó, false si no existe o no cambió nada.
     */
    public boolean reactivarPorCodigo(String codigoBarras) {
        int n = repo.reactivarPorCodigo(codigoBarras);
        log.info("Reactivar cb={} -> filas={}", codigoBarras, n);
        return n > 0;
    }
}
