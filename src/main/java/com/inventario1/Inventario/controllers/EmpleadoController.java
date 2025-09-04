// src/main/java/com/inventario1/Inventario/controllers/EmpleadoController.java
package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.models.Rol;
import com.inventario1.Inventario.repos.UsuarioRepository;
import com.inventario1.Inventario.models.Usuario;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Controller
public class EmpleadoController {

    private final UsuarioRepository usuarioRepository;

    public EmpleadoController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/empleados")
    public String empleadosLista(
            @RequestParam(name = "q", required = false) String q,
            Model model
    ) {
        // roles como texto (ejemplo)
        List<String> rolesTexto = Arrays.asList("bodeguero", "barra", "supervisor");

        // convertir a enum Rol: BODEGUERO, BARRA, SUPERVISOR
        List<Rol> rolesEnum = rolesTexto.stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .map(nombre -> {
                    try {
                        return Rol.valueOf(nombre);
                    } catch (IllegalArgumentException ex) {
                        return null; // descarta valores inválidos
                    }
                })
                .filter(r -> r != null)
                .toList();

        List<Usuario> empleados = usuarioRepository
                .findByRolInAndActivoTrueOrderByNombreAsc(rolesEnum);

        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase(Locale.ROOT).trim();
            empleados = empleados.stream()
                    .filter(u ->
                            (u.getNombre() != null && u.getNombre().toLowerCase(Locale.ROOT).contains(needle)) ||
                                    (u.getUsername() != null && u.getUsername().toLowerCase(Locale.ROOT).contains(needle)) ||
                                    (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(needle))
                    )
                    .toList();
        }

        model.addAttribute("empleados", empleados);
        model.addAttribute("q", q);
        model.addAttribute("page", 0);
        model.addAttribute("size", empleados.size());
        model.addAttribute("totalPages", 1);
        return "empleadoslista";
    }


}
