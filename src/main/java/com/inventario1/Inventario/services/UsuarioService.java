package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.Usuario;
import com.inventario1.Inventario.repos.UsuarioRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /* ===================== R U T  (9 = cuerpo+DV) ===================== */

    /** Deja solo [0-9K] y NO recorta. */
    public static String compact(String rutRaw) {
        if (rutRaw == null) return null;
        return rutRaw.toUpperCase().replaceAll("[^0-9K]", "");
    }

    public static String requireRutCompact9Valido(String rutRaw) {
        String s = compact(rutRaw);
        if (s == null || !s.matches("^\\d{7,8}[0-9K]$")) {
            throw new IllegalArgumentException("RUT inválido: debe ser 7–8 dígitos + DV (9 en total)");
        }
        String body = s.substring(0, s.length() - 1);
        String dv   = s.substring(s.length() - 1);
        if (!calcularDv(body).equals(dv)) {
            throw new IllegalArgumentException("RUT inválido: DV no coincide");
        }
        return s; // ya es compacto y válido
    }

    /** Módulo 11 (DV: 0–9 o K). */
    public static String calcularDv(String body) {
        int suma = 0, factor = 2;
        for (int i = body.length() - 1; i >= 0; i--) {
            int d = Character.digit(body.charAt(i), 10);
            suma += d * factor;
            factor = (factor == 7) ? 2 : factor + 1;
        }
        int r = 11 - (suma % 11);
        if (r == 11) return "0";
        if (r == 10) return "K";
        return Integer.toString(r);
    }

    /* ===================== C R U D ===================== */

    public Optional<Usuario> findByRut(String rutRaw) {
        String s = compact(rutRaw);
        if (s == null) return Optional.empty();
        return usuarioRepository.findById(s);
    }

    public List<Usuario> findAll() {
        return usuarioRepository.findAll();
    }

    /** Crear: exige y guarda SIEMPRE 9 (cuerpo+DV). */
    public Usuario create(Usuario usuario) {
        String rut9 = requireRutCompact9Valido(usuario.getRut());
        usuario.setRut(rut9);
        return usuarioRepository.save(usuario);
    }

    /** Actualizar por rut del path (9). Ignora cambios a la PK. */
    public Usuario update(String rutPath, Usuario cambios) {
        String key = requireRutCompact9Valido(rutPath);
        Usuario actual = usuarioRepository.findById(key)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + key));

        // NO cambiar la PK (rut)
        if (cambios.getNombre() != null)           actual.setNombre(cambios.getNombre());
        if (cambios.getUsername() != null)         actual.setUsername(cambios.getUsername());
        if (cambios.getEmail() != null)            actual.setEmail(cambios.getEmail());
        if (cambios.getTelefono() != null)         actual.setTelefono(cambios.getTelefono());
        if (cambios.getProfesion() != null)        actual.setProfesion(cambios.getProfesion());
        if (cambios.getRol() != null)              actual.setRol(cambios.getRol());
        if (cambios.getActivo() != null)           actual.setActivo(cambios.getActivo());
        if (cambios.getRequiereCambioPassword() != null)
            actual.setRequiereCambioPassword(cambios.getRequiereCambioPassword());
        if (cambios.getPasswordHash() != null && !cambios.getPasswordHash().isBlank())
            actual.setPasswordHash(cambios.getPasswordHash());

        if (cambios.getFoto() != null && cambios.getFoto().length > 0) {
            actual.setFoto(cambios.getFoto());
            actual.setFotoContentType(cambios.getFotoContentType());
            actual.setFotoNombre(cambios.getFotoNombre());
            actual.setFotoTamano(cambios.getFotoTamano());
        }
        return usuarioRepository.save(actual);
    }

    public void deleteByRut(String rutRaw) {
        String key = requireRutCompact9Valido(rutRaw);
        if (!usuarioRepository.existsById(key)) {
            throw new NoSuchElementException("Usuario no encontrado: " + key);
        }
        usuarioRepository.deleteById(key);
    }

    /** Guardar genérico exigiendo 9 válidos. */
    public Usuario saveValidatingRut9(Usuario usuario) {
        String rut9 = requireRutCompact9Valido(usuario.getRut());
        usuario.setRut(rut9);
        return usuarioRepository.save(usuario);
    }
}



