package com.inventario1.Inventario.security;

import com.inventario1.Inventario.repos.UsuarioRepository;
import com.inventario1.Inventario.models.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RutUserDetailsService implements UserDetailsService {
    private final UsuarioRepository repo;

    @Override
    public UserDetails loadUserByUsername(String rutInput) throws UsernameNotFoundException {
        String rut = rutInput.replaceAll("[^0-9kK]", "").toUpperCase();
        Usuario u = repo.findByRut(rut)
                .orElseThrow(() -> new UsernameNotFoundException("RUT no encontrado"));

        return new User(
                u.getRut(),                       // username = rut
                u.getPasswordHash(),              // BCrypt
                u.getActivo(),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRol().name()))
        );
    }
}
