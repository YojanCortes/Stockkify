// src/main/java/com/inventario1/Inventario/controllers/EmpleadoController.java
package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.models.Rol;
import com.inventario1.Inventario.models.Usuario;
import com.inventario1.Inventario.repos.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.beans.PropertyEditorSupport;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/empleados")
public class EmpleadoController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public EmpleadoController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Trim a todos los String que entren por formularios */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new PropertyEditorSupport() {
            @Override public void setAsText(String text) {
                setValue(text == null ? null : text.trim());
            }
        });
    }

    /** Pone los roles en el modelo para cualquier vista del controlador */
    @ModelAttribute("roles")
    public Rol[] roles() {
        return Rol.values();
    }

    // ===================== LISTA =====================
    @GetMapping
    public String lista(@RequestParam(name = "q", required = false) String q, Model model) {
        List<Rol> rolesEnum = Arrays.asList(Rol.BODEGUERO, Rol.BARRA, Rol.SUPERVISOR);
        List<Usuario> empleados = usuarioRepository.findByRolInAndActivoTrueOrderByNombreAsc(rolesEnum);

        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase(Locale.ROOT);
            empleados = empleados.stream().filter(u ->
                    (u.getNombre()   != null && u.getNombre().toLowerCase(Locale.ROOT).contains(needle)) ||
                            (u.getUsername() != null && u.getUsername().toLowerCase(Locale.ROOT).contains(needle)) ||
                            (u.getEmail()    != null && u.getEmail().toLowerCase(Locale.ROOT).contains(needle))
            ).toList();
        }

        model.addAttribute("empleados", empleados);
        model.addAttribute("q", q);
        model.addAttribute("page", 0);
        model.addAttribute("size", empleados.size());
        model.addAttribute("totalPages", 1);
        return "empleadoslista"; // templates/empleadoslista.html
    }

    // ===================== NUEVO (GET form) =====================
    @GetMapping("/nuevo")
    public String nuevoForm(Model model) {
        model.addAttribute("form", new EmpleadoController.EmpleadoForm());
        model.addAttribute("roles", Rol.values()); // o usa @ModelAttribute("roles")
        return "empleadosform"; // <-- nombre del template
    }

    // ===================== CREAR (POST) =====================
    @PostMapping
    public String crear(@Valid @ModelAttribute("form") EmpleadoForm form,
                        BindingResult binding, Model model) {

        if (binding.hasErrors()) {
            return "empleadosform";
        }

        // Rol requerido para generar profesión por defecto si está vacía
        if (form.getRol() == null) {
            binding.rejectValue("rol", "rol.requerido", "Debes seleccionar un rol");
            return "empleadosform";
        }

        // Autocompletar profesión si viene vacía
        String profesion = (form.getProfesion() == null || form.getProfesion().isBlank())
                ? profesionPorRol(form.getRol())
                : form.getProfesion();

        Usuario u = Usuario.builder()
                .username(form.getUsername())
                .email(vacioA(nullSiVacio(form.getEmail())))
                .passwordHash(passwordEncoder.encode(form.getPassword()))
                .requiereCambioPassword(Boolean.TRUE.equals(form.getRequiereCambioPassword()))
                .nombre(form.getNombre())
                .profesion(profesion)
                .rol(form.getRol())
                .activo(Boolean.TRUE.equals(form.getActivo()))
                .build();

        usuarioRepository.save(u);
        return "redirect:/empleados";
    }

    // ===================== DETALLE =====================
    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        model.addAttribute("empleado", u);
        return "empleadosdetalle"; // templates/empleadosdetalle.html
    }

    // ===================== EDITAR (GET form) =====================
    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        EmpleadoForm form = EmpleadoForm.from(u);
        model.addAttribute("form", form);
        model.addAttribute("id", id);
        return "empleadosform"; // reutiliza el mismo template
    }

    // ===================== ACTUALIZAR (POST) =====================
    @PostMapping("/{id}")
    public String actualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("form") EmpleadoForm form,
                             BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("id", id);
            return "empleadosform";
        }

        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        // Autocompletar profesión si quedó vacía
        String profesion = (form.getProfesion() == null || form.getProfesion().isBlank())
                ? profesionPorRol(form.getRol())
                : form.getProfesion();

        u.setNombre(form.getNombre());
        u.setProfesion(profesion);
        u.setEmail(vacioA(nullSiVacio(form.getEmail())));
        u.setUsername(form.getUsername());
        u.setRol(form.getRol());
        u.setActivo(Boolean.TRUE.equals(form.getActivo()));
        u.setRequiereCambioPassword(Boolean.TRUE.equals(form.getRequiereCambioPassword()));

        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }

        usuarioRepository.save(u);
        return "redirect:/empleados/" + id;
    }

    // ===================== Helpers =====================
    private static String profesionPorRol(Rol rol) {
        return switch (rol) {
            case BODEGUERO -> "Bodeguero";
            case BARRA     -> "Barra";
            case SUPERVISOR-> "Supervisor";
        };
    }

    private static String nullSiVacio(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
    private static String vacioA(String s) { return s; }

    // ===================== DTO (Form) =====================
    public static class EmpleadoForm {
        @NotBlank @Size(max = 64)
        private String username;

        @Email @Size(max = 120)
        private String email;

        @Size(min = 0)
        private String password;

        @NotBlank @Size(max = 120)
        private String nombre;

        @Size(max = 120)
        private String profesion;

        private Rol rol;

        private Boolean activo = true;
        private Boolean requiereCambioPassword = false;

        public static EmpleadoForm from(Usuario u) {
            EmpleadoForm f = new EmpleadoForm();
            f.username = u.getUsername();
            f.email = u.getEmail();
            f.nombre = u.getNombre();
            f.profesion = u.getProfesion();
            f.rol = u.getRol();
            f.activo = u.getActivo();
            f.requiereCambioPassword = u.getRequiereCambioPassword();
            return f;
        }

        // Getters / Setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getProfesion() { return profesion; }
        public void setProfesion(String profesion) { this.profesion = profesion; }
        public Rol getRol() { return rol; }
        public void setRol(Rol rol) { this.rol = rol; }
        public Boolean getActivo() { return activo; }
        public void setActivo(Boolean activo) { this.activo = activo; }
        public Boolean getRequiereCambioPassword() { return requiereCambioPassword; }
        public void setRequiereCambioPassword(Boolean requiereCambioPassword) { this.requiereCambioPassword = requiereCambioPassword; }
    }
}
