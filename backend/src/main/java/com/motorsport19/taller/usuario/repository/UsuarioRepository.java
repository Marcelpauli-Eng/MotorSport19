package com.motorsport19.taller.usuario.repository;

import com.motorsport19.taller.usuario.domain.Permiso;
import com.motorsport19.taller.usuario.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByUsernameAndActivoTrue(String username);

    /**
     * Usuarios activos cuyo rol concede un permiso.
     *
     * <p>Sustituye al viejo «dame los del rol TECNICO». Con los roles clavados en
     * el codigo aquello valia; ahora que los compone el administrador, un taller
     * puede tener dos roles distintos que trabajan ordenes y ninguno llamarse
     * «tecnico». Lo que define a quien recibe trabajo es lo que puede hacer.
     */
    @Query("""
            SELECT u FROM Usuario u
             WHERE u.activo = TRUE AND :permiso MEMBER OF u.rol.permisos
             ORDER BY u.nombreCompleto
            """)
    List<Usuario> buscarConPermiso(@Param("permiso") Permiso permiso);

    /** Cuantos usuarios activos pueden repartir permisos. Nunca puede quedar cero. */
    @Query("""
            SELECT COUNT(u) FROM Usuario u
             WHERE u.activo = TRUE AND :permiso MEMBER OF u.rol.permisos
            """)
    long contarConPermiso(@Param("permiso") Permiso permiso);
}
