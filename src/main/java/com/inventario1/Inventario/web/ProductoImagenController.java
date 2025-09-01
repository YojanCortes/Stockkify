package com.inventario1.Inventario.web;

import com.inventario1.Inventario.files.FileStorageService;
import com.inventario1.Inventario.repos.ProductoRepository;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;

@Controller
public class ProductoImagenController {

    private final FileStorageService storage;
    private final ProductoRepository repo;

    public ProductoImagenController(FileStorageService storage, ProductoRepository repo) {
        this.storage = storage;
        this.repo = repo;
    }

    // Ver imagen del producto (con placeholder si no existe)
    @GetMapping("/productos/{codigo}/imagen")
    public ResponseEntity<Resource> verImagen(@PathVariable String codigo) {
        try {
            Path p = storage.findExisting(codigo);
            Resource res;
            MediaType type;
            if (p != null) {
                res = new FileSystemResource(p);
                String name = p.getFileName().toString().toLowerCase();
                if (name.endsWith(".png")) type = MediaType.IMAGE_PNG;
                else if (name.endsWith(".webp")) type = MediaType.parseMediaType("image/webp");
                else type = MediaType.IMAGE_JPEG;
            } else {
                res = new ClassPathResource("static/img/no-image.png");
                type = MediaType.IMAGE_PNG;
            }
            return ResponseEntity.ok().contentType(type).body(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // Formulario para subir imagen
    @GetMapping("/admin/productos/{codigo}/imagen")
    public String formImagen(@PathVariable String codigo, Model model) {
        model.addAttribute("codigo", codigo);
        model.addAttribute("existe", repo.existsById(codigo));
        return "producto_imagen";
    }

    // Subir imagen (jpg/png/webp)
    @PostMapping(value = "/admin/productos/{codigo}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String subirImagen(@PathVariable String codigo, @RequestParam("file") MultipartFile file, Model model) {
        try {
            if (!repo.existsById(codigo)) {
                model.addAttribute("error", "El producto " + codigo + " no existe");
            } else {
                storage.saveForCodigo(codigo, file);
                model.addAttribute("ok", "Imagen actualizada para " + codigo);
            }
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("codigo", codigo);
        model.addAttribute("existe", repo.existsById(codigo));
        return "producto_imagen";
    }
}
