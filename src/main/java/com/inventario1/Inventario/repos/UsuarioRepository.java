// src/main/java/com/inventario1/Inventario/repos/UsuarioRepository.java
package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.Usuario;
import com.inventario1.Inventario.models.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);
    boolean existsByUsername(String username);  // 👈 AQUI
    List<Usuario> findByRolInAndActivoTrueOrderByNombreAsc(Set<Rol> roles);
}
