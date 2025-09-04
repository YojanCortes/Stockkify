package com.inventario1.Inventario.repos;

import com.inventario1.Inventario.models.Usuario;
import com.inventario1.Inventario.models.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;


@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    List<Usuario> findByRolInAndActivoTrueOrderByNombreAsc(Collection<Rol> roles);
    List<Usuario> findByActivoTrueOrderByNombreAsc(); // <- deja este para la prueba del paso 1
}
