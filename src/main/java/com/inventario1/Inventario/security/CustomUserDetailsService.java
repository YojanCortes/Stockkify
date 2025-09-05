// src/main/java/com/inventario1/Inventario/security/CustomUserDetailsService.java
package com.inventario1.Inventario.security;

import com.inventario1.Inventario.repos.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    // "username" aquí ES el RUT porque el form manda name="rut"
    @Override
    public UserDetails loadUserByUsername(String rut) throws UsernameNotFoundException {
        var u = usuarioRepository.findByRut(rut)
                .orElseThrow(() -> new UsernameNotFoundException("RUT no encontrado: " + rut));

        return User.builder()
                .username(u.getRut())                 // principal = RUT
                .password(u.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + u.getRol().name())))
                .disabled(Boolean.FALSE.equals(u.getActivo()))
                .build();
    }
}
