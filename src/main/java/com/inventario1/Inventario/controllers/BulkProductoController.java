package com.inventario1.Inventario.controllers;

// bulk/controller/BulkProductoController.java

import com.inventario1.Inventario.models.Producto;
import com.inventario1.Inventario.services.dto.BulkProductoService;
import com.inventario1.Inventario.services.dto.BulkProductosForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/productos")
public class BulkProductoController {

    private final BulkProductoService bulkService;

    public BulkProductoController(BulkProductoService bulkService) {
        this.bulkService = bulkService;
    }

    /** Evita que el navegador setee campos sensibles desde el form */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("items[*].id", "items[*].creadoEn", "items[*].actualizadoEn");
    }

    /** GET /productos/agregar-varios → muestra la vista con 5 filas por defecto */
    @GetMapping("/agregar-varios") // OJO: NO repetir "/productos" aquí
    public String view(Model model) {
        if (!model.containsAttribute("form")) {
            var form = new BulkProductosForm(); // contiene List<Producto> items
            for (int i = 0; i < 5; i++) form.getItems().add(new Producto());
            model.addAttribute("form", form);   // nombre EXACTO: "form"
        }
        return "agregar_producto_varios"; // /resources/templates/agregar_producto_varios.html
    }

    /** POST /productos/agregar-varios → procesa el lote (dryRun/upsert opcionales) */
    @PostMapping(value = "/agregar-varios", consumes = "multipart/form-data")
    public String submit(@RequestParam(name = "dryRun", defaultValue = "false") boolean dryRun,
                         @RequestParam(name = "upsert", defaultValue = "false") boolean upsert,
                         @Valid @ModelAttribute("form") BulkProductosForm form, // BindingResult debe ir justo después
                         BindingResult br,
                         Model model) {

        // Errores de Bean Validation por item
        if (br.hasErrors()) {
            return "agregar_producto_varios";
        }

        // Errores globales (duplicados en el lote, etc.)
        var globalErrors = bulkService.validateItems(form.getItems());
        if (!globalErrors.isEmpty()) {
            model.addAttribute("globalErrors", globalErrors);
            return "agregar_producto_varios";
        }

        // Proceso principal: normaliza, verifica existentes y guarda/actualiza por bloques
        BulkProductoService.BulkResult result =
                bulkService.process(form.getItems(), dryRun, upsert, 100);

        model.addAttribute("bulkResult", result);

        // Si es simulación, re-renderiza con el resumen
        if (dryRun) return "agregar_producto_varios";

        // En operación real, redirige (ajusta si prefieres otra página)
        return "redirect:/buscar";
    }
}
