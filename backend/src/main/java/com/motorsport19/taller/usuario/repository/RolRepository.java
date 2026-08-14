package com.motorsport19.taller.usuario.repository;

import com.motorsport19.taller.usuario.domain.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Long> {

    List<Rol> findAllByOrderByNombreAsc();

    List<Rol> findByActivoTrueOrderByNombreAsc();

    /**
     * Busca por nombre sin distinguir mayusculas.
     *
     * <p>Para avisar del duplicado antes de que lo haga el indice unico, que
     * suelta un mensaje que no dice nada a quien esta rellenando el formulario.
     */
    @Query("SELECT r FROM Rol r WHERE upper(r.nombre) = upper(:nombre)")
    Optional<Rol> buscarPorNombre(@Param("nombre") String nombre);

    /** Cuantos usuarios llevan este rol. Un rol con gente no se borra. */
    @Query("SELECT COUNT(u) FROM Usuario u WHERE u.rol.id = :rolId AND u.activo = TRUE")
    long contarUsuariosActivos(@Param("rolId") Long rolId);
}
