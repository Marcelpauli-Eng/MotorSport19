package com.motorsport19.taller.usuario.repository;

import com.motorsport19.taller.usuario.domain.Rol;
import com.motorsport19.taller.usuario.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByUsernameAndActivoTrue(String username);

    @Query("SELECT u FROM Usuario u WHERE u.rol = :rol AND u.activo = TRUE ORDER BY u.nombreCompleto")
    List<Usuario> buscarPorRol(@Param("rol") Rol rol);
}
