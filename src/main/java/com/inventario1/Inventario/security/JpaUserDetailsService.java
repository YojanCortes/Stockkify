package com.inventario1.Inventario.security;

import com.inventario1.Inventario.models.Usuario;
import com.inventario1.Inventario.repos.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {
    private final UsuarioRepository repo;

    @Override
    public UserDetails loadUserByUsername(String rutIngresado) throws UsernameNotFoundException {
        String rut = rutIngresado == null ? null : rutIngresado.trim();
        return repo.findByRut(rut)
                .map(this::toUser)
                .orElseThrow(() -> new UsernameNotFoundException("RUT no encontrado"));
    }

    private UserDetails toUser(Usuario u) {
        return User.builder()
                .username(u.getRut()) // el "username" en Spring será el rut
                .password(u.getPasswordHash())
                .disabled(!Boolean.TRUE.equals(u.getActivo()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + u.getRol().name()))
                .build();
    }
}
