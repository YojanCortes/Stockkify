package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.Usuario;
import com.inventario1.Inventario.models.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface UsuarioRepository extends JpaRepository<Usuario, String> { // PK = rut
    Optional<Usuario> findByRut(String rut);
    boolean existsByRut(String rut);

    // si aún generas username, deja esto:
    boolean existsByUsername(String username);

    List<Usuario> findByRolInAndActivoTrueOrderByNombreAsc(Set<Rol> roles);
}
