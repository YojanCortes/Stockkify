// src/main/java/com/inventario1/Inventario/controllers/EmpleadoController.java
package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.models.Rol;
import com.inventario1.Inventario.models.Usuario;
import com.inventario1.Inventario.repos.UsuarioRepository;
import com.inventario1.Inventario.web.EmpleadoForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.beans.PropertyEditorSupport;
import java.util.*;

@Controller
@RequestMapping("/empleados")
public class EmpleadoController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public EmpleadoController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Trim a todos los String
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new PropertyEditorSupport() {
            @Override public void setAsText(String text) { setValue(text == null ? null : text.trim()); }
        });
    }

    // Roles para las vistas
    @ModelAttribute("roles")
    public Rol[] roles() { return Rol.values(); }

    // LISTA
    @GetMapping
    public String lista(@RequestParam(name = "q", required = false) String q, Model model) {
        Set<Rol> rolesEnum = EnumSet.of(Rol.BODEGUERO, Rol.BARRA, Rol.SUPERVISOR);
        var empleados = usuarioRepository.findByRolInAndActivoTrueOrderByNombreAsc(rolesEnum);

        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase(Locale.ROOT);
            empleados = empleados.stream().filter(u ->
                    (u.getNombre() != null && u.getNombre().toLowerCase(Locale.ROOT).contains(needle)) ||
                            (u.getUsername() != null && u.getUsername().toLowerCase(Locale.ROOT).contains(needle)) ||
                            (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(needle))
            ).toList();
        }

        model.addAttribute("empleados", empleados);
        model.addAttribute("q", q);
        model.addAttribute("page", 0);
        model.addAttribute("size", empleados.size());
        model.addAttribute("totalPages", 1);
        return "empleadoslista";
    }

    // NUEVO (GET)
    @GetMapping("/nuevo")
    public String nuevoForm(Model model) {
        model.addAttribute("form", new EmpleadoForm());
        return "empleadosform";
    }

    // CREAR (POST) - devuelve JSON si es AJAX, o redirect si no
    @PostMapping
    public Object crear(@Valid @ModelAttribute("form") EmpleadoForm form,
                        BindingResult binding,
                        Model model,
                        HttpServletRequest request) {

        boolean ajax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));

        // Validaciones manuales mínimas (porque quitaste campos del HTML)
        if (form.getRol() == null) {
            binding.rejectValue("rol", "rol.requerido", "Debes seleccionar un rol");
        }
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            binding.rejectValue("password", "password.requerido", "La contraseña es obligatoria");
        }
        if (form.getConfirmPassword() == null || !form.getConfirmPassword().equals(form.getPassword())) {
            binding.rejectValue("confirmPassword", "password.noCoincide", "Las contraseñas no coinciden");
        }

        if (binding.hasErrors()) {
            if (ajax) return ResponseEntity.badRequest().body(Map.of("error", "Validación falló", "fields", binding.getAllErrors()));
            return "empleadosform";
        }

        // Username automático (a partir del email)
        String username = generarUsernameDesdeEmail(form.getEmail());

        // Asegurar unicidad
        username = deduplicarUsername(username);

        // Profesión por rol (como ya no la pides en el HTML)
        String profesion = switch (form.getRol()) {
            case BODEGUERO -> "Bodeguero";
            case BARRA -> "Barra";
            case SUPERVISOR -> "Supervisor";
        };

        Usuario u = Usuario.builder()
                .username(username)
                .email(nullSiVacio(form.getEmail()))
                .telefono(nullSiVacio(form.getTelefono()))
                .passwordHash(passwordEncoder.encode(form.getPassword()))
                .requiereCambioPassword(Boolean.TRUE.equals(form.getRequiereCambioPassword()))
                .nombre(form.getNombre())
                .profesion(profesion)
                .rol(form.getRol())
                .activo(true) // por defecto
                .build();

        usuarioRepository.save(u);

        if (ajax) return ResponseEntity.ok(Map.of("ok", true, "id", u.getId(), "username", u.getUsername()));
        return "redirect:/empleados";
    }

    // DETALLE
    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        model.addAttribute("empleado", u);
        return "empleadosdetalle";
    }

    // EDITAR (GET)
    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        EmpleadoForm form = new EmpleadoForm();
        form.setId(u.getId());
        form.setNombre(u.getNombre());
        form.setEmail(u.getEmail());
        form.setTelefono(u.getTelefono());
        form.setRol(u.getRol());
        form.setProfesion(u.getProfesion());
        form.setActivo(u.getActivo());
        form.setRequiereCambioPassword(u.getRequiereCambioPassword());
        model.addAttribute("form", form);
        model.addAttribute("id", id);
        return "empleadosform";
    }

    // ACTUALIZAR (POST) - JSON si AJAX; redirect si no
    @PostMapping("/{id}")
    public Object actualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("form") EmpleadoForm form,
                             BindingResult binding,
                             Model model,
                             HttpServletRequest request) {
        boolean ajax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));

        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        if (form.getRol() == null) {
            binding.rejectValue("rol", "rol.requerido", "Debes seleccionar un rol");
        }
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            if (form.getConfirmPassword() == null || !form.getConfirmPassword().equals(form.getPassword())) {
                binding.rejectValue("confirmPassword", "password.noCoincide", "Las contraseñas no coinciden");
            }
        }

        if (binding.hasErrors()) {
            if (ajax) return ResponseEntity.badRequest().body(Map.of("error", "Validación falló", "fields", binding.getAllErrors()));
            model.addAttribute("id", id);
            return "empleadosform";
        }

        String profesion = switch (form.getRol()) {
            case BODEGUERO -> "Bodeguero";
            case BARRA -> "Barra";
            case SUPERVISOR -> "Supervisor";
        };

        u.setNombre(form.getNombre());
        u.setEmail(nullSiVacio(form.getEmail()));
        u.setTelefono(nullSiVacio(form.getTelefono()));
        u.setRol(form.getRol());
        u.setProfesion(profesion);
        u.setActivo(Boolean.TRUE.equals(form.getActivo()));
        u.setRequiereCambioPassword(Boolean.TRUE.equals(form.getRequiereCambioPassword()));
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }

        usuarioRepository.save(u);

        if (ajax) return ResponseEntity.ok(Map.of("ok", true, "id", u.getId()));
        return "redirect:/empleados/" + id;
    }

    // ===== Helpers =====
    private static String nullSiVacio(String s) { return (s == null || s.isBlank()) ? null : s; }

    private String generarUsernameDesdeEmail(String email) {
        if (email == null || email.isBlank()) return "user";
        String base = email.split("@")[0].toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "");
        if (base.isBlank()) base = "user";
        return base;
    }

    private String deduplicarUsername(String base) {
        String candidate = base;
        int i = 1;
        while (usuarioRepository.existsByUsername(candidate)) {
            candidate = base + i;
            i++;
        }
        return candidate;
    }
}
