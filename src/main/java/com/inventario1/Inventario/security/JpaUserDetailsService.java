package com.inventario1.Inventario.security;

import com.inventario1.Inventario.models.Usuario;
import com.inventario1.Inventario.repos.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String rutOrUsername) throws UsernameNotFoundException {
        // Usa el método que ya tienes en tu repositorio:
        // Si más adelante cambias a RUT explícito, crea findByRut(...) y reemplaza aquí.
        Usuario u = usuarioRepository.findByUsername(rutOrUsername)
                .orElseThrow(() -> new UsernameNotFoundException("No existe el usuario: " + rutOrUsername));

        // Si manejas "activo" en la tabla
        if (Boolean.FALSE.equals(u.getActivo())) {
            throw new DisabledException("Usuario inactivo");
        }

        // Mapea tu enum Rol -> "ROLE_<ROL>"
        return User.withUsername(u.getUsername())      // Aquí guardas el RUT/username
                .password(u.getPasswordHash())         // Debe estar en BCrypt
                .roles(u.getRol().name())              // SUPERVISOR, BODEGUERO, BARRA, etc.
                .build();
    }
}
