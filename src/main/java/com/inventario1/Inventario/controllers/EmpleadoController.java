// src/main/java/com/inventario1/Inventario/controllers/EmpleadoController.java
package com.inventario1.Inventario.controllers;

import com.inventario1.Inventario.models.Rol;
import com.inventario1.Inventario.models.Usuario;
import com.inventario1.Inventario.repos.UsuarioRepository;
import com.inventario1.Inventario.web.EmpleadoForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
                    (u.getNombre()   != null && u.getNombre().toLowerCase(Locale.ROOT).contains(needle)) ||
                            (u.getUsername() != null && u.getUsername().toLowerCase(Locale.ROOT).contains(needle)) ||
                            (u.getEmail()    != null && u.getEmail().toLowerCase(Locale.ROOT).contains(needle)) ||
                            (u.getRut()      != null && u.getRut().toLowerCase(Locale.ROOT).contains(needle))
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

    // CREAR (POST) - JSON si AJAX; redirect si no
    @PostMapping
    public Object crear(@Valid @ModelAttribute("form") EmpleadoForm form,
                        BindingResult binding,
                        Model model,
                        HttpServletRequest request) {

        boolean ajax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));

        // Validaciones mínimas
        if (form.getRol() == null) {
            binding.rejectValue("rol", "rol.requerido", "Debes seleccionar un rol");
        }
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            binding.rejectValue("password", "password.requerido", "La contraseña es obligatoria");
        }
        if (form.getConfirmPassword() == null || !form.getConfirmPassword().equals(form.getPassword())) {
            binding.rejectValue("confirmPassword", "password.noCoincide", "Las contraseñas no coinciden");
        }
        if (form.getRut() == null || form.getRut().isBlank()) {
            binding.rejectValue("rut", "rut.requerido", "El RUT es obligatorio");
        }

        // Normaliza/valida y guarda en formato compacto (cuerpo+DV, sin puntos/guion)
        String rutCompacto = normalizarRutCompacto(form.getRut());
        if (rutCompacto == null) {
            binding.rejectValue("rut", "rut.invalido", "RUT inválido");
        } else if (existsByRutFlexible(rutCompacto)) {
            binding.rejectValue("rut", "rut.duplicado", "Ya existe un usuario con ese RUT");
        }

        if (binding.hasErrors()) {
            if (ajax) return ResponseEntity.badRequest().body(Map.of("error", "Validación falló", "fields", binding.getAllErrors()));
            return "empleadosform";
        }

        // Username automático (a partir del email)
        String username = generarUsernameDesdeEmail(form.getEmail());
        username = deduplicarUsername(username);

        // Profesión por rol
        String profesion = switch (form.getRol()) {
            case BODEGUERO -> "Bodeguero";
            case BARRA     -> "Barra";
            case SUPERVISOR-> "Supervisor";
        };

        Usuario u = Usuario.builder()
                .rut(rutCompacto) // ✅ guarda SIN puntos ni guion (cuerpo+DV)
                .username(username)
                .email(nullSiVacio(form.getEmail()))
                .telefono(nullSiVacio(form.getTelefono()))
                .passwordHash(passwordEncoder.encode(form.getPassword()))
                .requiereCambioPassword(Boolean.TRUE.equals(form.getRequiereCambioPassword()))
                .nombre(form.getNombre())
                .profesion(profesion)
                .rol(form.getRol())
                .activo(true)
                .build();

        usuarioRepository.save(u);

        if (ajax) return ResponseEntity.ok(Map.of("ok", true, "rut", u.getRut(), "username", u.getUsername()));
        return "redirect:/empleados";
    }

    // ====== DETALLE → redirige a EDITAR ======
    @GetMapping("/{rut}")
    public String detalle(@PathVariable String rut) {
        return "redirect:/empleados/" + rut + "/editar";
    }

    // EDITAR (GET) — usa empleadosformedit.html
    @GetMapping("/{rut}/editar")
    public String editarForm(@PathVariable String rut, Model model) {
        Usuario u = findByRutFlexibleOr404(rut);
        EmpleadoForm form = new EmpleadoForm();
        form.setRut(u.getRut()); // compacto; el front lo mostrará con puntos/guion de forma visual
        form.setNombre(u.getNombre());
        form.setEmail(u.getEmail());
        form.setTelefono(u.getTelefono());
        form.setRol(u.getRol());
        form.setProfesion(u.getProfesion());
        form.setActivo(u.getActivo());
        form.setRequiereCambioPassword(u.getRequiereCambioPassword());
        model.addAttribute("form", form);
        return "empleadosformedit";
    }

    // ACTUALIZAR (POST) - JSON si AJAX; redirect si no
    @PostMapping("/{rut}")
    public Object actualizar(@PathVariable String rut,
                             @Valid @ModelAttribute("form") EmpleadoForm form,
                             BindingResult binding,
                             Model model,
                             HttpServletRequest request) {
        boolean ajax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
        Usuario u = findByRutFlexibleOr404(rut);

        if (form.getRol() == null) {
            binding.rejectValue("rol", "rol.requerido", "Debes seleccionar un rol");
        }
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            if (form.getConfirmPassword() == null || !form.getConfirmPassword().equals(form.getPassword())) {
                binding.rejectValue("confirmPassword", "password.noCoincide", "Las contraseñas no coinciden");
            }
        }

        // No permitimos cambiar el RUT en edición (PK). Si lo tocan, lo ignoramos o marcamos error leve.
        if (form.getRut() != null && !form.getRut().isBlank()) {
            String nuevoCompacto = normalizarRutCompacto(form.getRut());
            if (nuevoCompacto == null) {
                binding.rejectValue("rut", "rut.invalido", "RUT inválido");
            } else if (!nuevoCompacto.equals(u.getRut())) {
                // Mantener el original
                form.setRut(u.getRut());
            }
        }

        if (binding.hasErrors()) {
            if (ajax) return ResponseEntity.badRequest().body(Map.of("error", "Validación falló", "fields", binding.getAllErrors()));
            return "empleadosformedit";
        }

        String profesion = switch (form.getRol()) {
            case BODEGUERO -> "Bodeguero";
            case BARRA     -> "Barra";
            case SUPERVISOR-> "Supervisor";
        };

        u.setNombre(form.getNombre());
        u.setEmail(nullSiVacio(form.getEmail()));
        u.setTelefono(nullSiVacio(form.getTelefono()));
        u.setRol(form.getRol());
        u.setProfesion(profesion);

        if (form.getActivo() != null) u.setActivo(form.getActivo());
        if (form.getRequiereCambioPassword() != null) u.setRequiereCambioPassword(form.getRequiereCambioPassword());

        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }

        usuarioRepository.save(u);

        if (ajax) return ResponseEntity.ok(Map.of("ok", true, "rut", u.getRut()));
        return "redirect:/empleados/" + u.getRut() + "/editar";
    }

    // ====== ELIMINAR (borrado lógico) ======
    @PostMapping("/{rut}/eliminar")
    public Object eliminar(@PathVariable String rut, HttpServletRequest request) {
        boolean ajax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
        Usuario u = findByRutFlexibleOr404(rut);
        u.setActivo(false);
        usuarioRepository.save(u);

        if (ajax) return ResponseEntity.ok(Map.of("ok", true));
        return "redirect:/empleados";
    }

    // ===================== Imagen =====================

    // SUBIR FOTO (BD) — POST /empleados/{rut}/foto
    @PostMapping("/{rut}/foto")
    public ResponseEntity<?> subirFoto(@PathVariable String rut,
                                       @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Archivo vacío"));
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("ok", false, "error", "Solo se permiten imágenes"));
        }
        if (file.getSize() > 2_000_000) { // 2MB
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("ok", false, "error", "La imagen no debe superar 2MB"));
        }

        Usuario u = findByRutFlexibleOr404(rut);

        u.setFoto(file.getBytes());
        u.setFotoContentType(ct);
        u.setFotoNombre(file.getOriginalFilename());
        u.setFotoTamano(file.getSize());
        usuarioRepository.save(u);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // VER FOTO — GET /empleados/{rut}/foto
    @GetMapping("/{rut}/foto")
    public ResponseEntity<byte[]> verFoto(@PathVariable String rut) {
        Usuario u = findByRutFlexibleOr404(rut);

        byte[] bytes = u.getFoto();
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaType.IMAGE_JPEG;
        try {
            if (u.getFotoContentType() != null) mediaType = MediaType.parseMediaType(u.getFotoContentType());
        } catch (Exception ignored) {}

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(bytes);
    }

    // ===================== Helpers =====================
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

    /** Calcula DV (Módulo 11) para un cuerpo numérico. */
    private static String calcularDV(String body) {
        int suma = 0, factor = 2;
        for (int i = body.length() - 1; i >= 0; i--) {
            suma += Character.digit(body.charAt(i), 10) * factor;
            factor = (factor == 7) ? 2 : factor + 1;
        }
        int resto = 11 - (suma % 11);
        if (resto == 11) return "0";
        if (resto == 10) return "K";
        return Integer.toString(resto);
    }

    /** Normaliza a formato compacto (SIN puntos ni guion): cuerpo+DV (p.ej. 12345678K).
     *  Acepta:
     *   - sólo dígitos (4..12): calcula DV y lo anexa;
     *   - dígitos + DV final (3..11 dígitos + [0-9K]): valida DV.
     *  Devuelve null si no puede normalizar.
     */
    private static String normalizarRutCompacto(String input) {
        if (input == null) return null;
        String raw = input.replaceAll("[^0-9Kk]", "").toUpperCase();
        if (raw.isEmpty()) return null;

        // Solo dígitos -> calcula DV
        if (raw.matches("^\\d{4,12}$")) {
            String body = raw;
            return body + calcularDV(body);
        }

        // Dígitos + DV provisto -> valida
        if (raw.matches("^\\d{3,11}[0-9K]$")) {
            String body = raw.substring(0, raw.length() - 1);
            String dv   = raw.substring(raw.length() - 1);
            return calcularDV(body).equals(dv) ? body + dv : null;
        }
        return null;
    }

    /** Pasa compacto -> con guion (#######-X) para compatibilidad con datos antiguos. */
    private static String formatearConGuion(String compacto) {
        if (compacto == null || compacto.length() < 2) return null;
        String body = compacto.substring(0, compacto.length() - 1);
        String dv   = compacto.substring(compacto.length() - 1);
        return body + "-" + dv;
    }

    /** Búsqueda “flexible”: prueba compacto (nuevo), luego legacy (con guion) y, por seguridad, el raw. */
    private Optional<Usuario> findByRutFlexible(String rutEntrada) {
        String compacto = normalizarRutCompacto(rutEntrada);
        if (compacto != null) {
            Optional<Usuario> hit = usuarioRepository.findByRut(compacto);
            if (hit.isPresent()) return hit;
            String legacy = formatearConGuion(compacto);
            if (legacy != null) {
                hit = usuarioRepository.findByRut(legacy);
                if (hit.isPresent()) return hit;
            }
        }
        // último intento literal por compatibilidad
        return usuarioRepository.findByRut(rutEntrada);
    }

    private Usuario findByRutFlexibleOr404(String rutEntrada) {
        return findByRutFlexible(rutEntrada)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
    }

    /** ¿Existe ya (en compacto o legacy con guion)? */
    private boolean existsByRutFlexible(String rutEntrada) {
        String compacto = normalizarRutCompacto(rutEntrada);
        if (compacto == null) return false;
        if (usuarioRepository.existsByRut(compacto)) return true;
        String legacy = formatearConGuion(compacto);
        if (legacy != null && usuarioRepository.existsByRut(legacy)) return true;
        return usuarioRepository.existsByRut(rutEntrada);
    }
}
