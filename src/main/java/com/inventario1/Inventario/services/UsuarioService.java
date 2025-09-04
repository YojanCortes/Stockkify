package com.inventario1.Inventario.services;

import com.inventario1.Inventario.models.Rol;
import com.inventario1.Inventario.models.Usuario;
import com.inventario1.Inventario.repos.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class UsuarioService {

    private final UsuarioRepository repo;

    public UsuarioService(UsuarioRepository repo) {
        this.repo = repo;
    }

    public List<Usuario> listarEmpleadosVisibles() {
        Set<Rol> roles = EnumSet.of(Rol.BARRA, Rol.BODEGUERO, Rol.SUPERVISOR);
        return repo.findByRolInAndActivoTrueOrderByNombreAsc(roles);
    }

}
