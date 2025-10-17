// path: src/main/java/com/inventario1/Inventario/web/ProductosApiController.java
package com.inventario1.Inventario.web;

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.repos.ProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/productos/api")
public class ProductosApiController {

    private final ProductoRepository productoRepository;

    public ProductosApiController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @GetMapping("/by-barcode/{codigo}")
    public ResponseEntity<?> byBarcode(@PathVariable String codigo) {
        // Usa el que prefieras; ambos existen en tu repo
        Optional<Producto> opt = productoRepository.findByCodigoBarras(codigo);
        // Optional<Producto> opt = productoRepository.findByCodigoBarrasAndActivoTrue(codigo);

        return opt.<ResponseEntity<?>>map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigoBarras", p.getCodigoBarras());
            m.put("nombre", p.getNombre());
            m.put("marca", p.getMarca()); // puede ser null
            m.put("precio", p.getPrecio());
            m.put("categoria", p.getCategoria()); // enum o null
            m.put("unidadBase", p.getUnidadBase()); // enum o null
            m.put("volumenNominalMl", p.getVolumenNominalMl()); // puede ser null
            m.put("graduacionAlcoholica", p.getGraduacionAlcoholica()); // puede ser null
            m.put("fechaVencimiento", p.getFechaVencimiento()); // puede ser null
            m.put("perecible", p.getPerecible());
            m.put("retornable", p.getRetornable());
            m.put("stockActual", p.getStockActual()); // ← este es el que editas en el form
            m.put("fotoUrl", p.getImagenUrl()); // puede ser null
            return ResponseEntity.ok(m);
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
