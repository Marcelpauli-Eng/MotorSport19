package com.motorsport19.taller.usuario.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.usuario.domain.Permiso;
import com.motorsport19.taller.usuario.domain.Rol;
import com.motorsport19.taller.usuario.repository.RolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Alta y mantenimiento de los roles del taller.
 *
 * <p>Los compone el administrador a partir del catalogo de {@link Permiso}. El
 * catalogo no se puede ampliar desde aqui a proposito: un permiso que ningun
 * sitio del codigo comprueba seria una casilla que no protege nada.
 */
@Service
public class RolService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RolService.class);

    private final RolRepository rolRepository;

    public RolService(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    /**
     * Mantiene al dia el rol de administracion en cada arranque.
     *
     * <p>Un permiso nuevo del catalogo tiene que llegarle solo. Si hubiera que
     * acordarse de marcarlo a mano, la primera funcion que se anadiera dejaria
     * al administrador sin poder usarla, y sin nadie que pudiera concedersela.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        rolRepository.findById(Rol.ID_ADMINISTRACION).ifPresent(administracion -> {
            if (administracion.getPermisos().size() != Permiso.values().length) {
                administracion.concederTodo();
                log.info("Rol de administracion actualizado con los {} permisos del catalogo.",
                        Permiso.values().length);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<Rol> listar(boolean soloActivos) {
        return soloActivos
                ? rolRepository.findByActivoTrueOrderByNombreAsc()
                : rolRepository.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public Rol obtener(Long id) {
        return rolRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el rol", id));
    }

    /** Cuantos usuarios activos lleva cada rol, para la pantalla de roles. */
    @Transactional(readOnly = true)
    public long usuariosDe(Long rolId) {
        return rolRepository.contarUsuariosActivos(rolId);
    }

    @Transactional
    public Rol crear(String nombre, String descripcion, Set<Permiso> permisos) {
        exigirNombreLibre(nombre, null);
        Rol rol = rolRepository.save(Rol.crear(nombre, descripcion, permisos));
        log.info("Creado el rol '{}' con {} permisos", rol.getNombre(), rol.getPermisos().size());
        return rol;
    }

    @Transactional
    public Rol actualizar(Long id, String nombre, String descripcion, Set<Permiso> permisos) {
        Rol rol = obtener(id);
        exigirNombreLibre(nombre, id);
        rol.actualizar(nombre, descripcion, permisos);
        log.info("Actualizado el rol '{}': {} permisos", rol.getNombre(), rol.getPermisos().size());
        return rol;
    }

    /**
     * Abre o cierra un rol.
     *
     * <p>Cerrarlo no borra nada ni echa a nadie: deja de admitir usuarios nuevos.
     * Con gente dentro no se cierra, porque eso dejaria a esas personas con un
     * rol que ya nadie mantiene.
     */
    @Transactional
    public Rol cambiarEstado(Long id, boolean activo) {
        Rol rol = obtener(id);

        if (!activo) {
            long usuarios = rolRepository.contarUsuariosActivos(id);
            if (usuarios > 0) {
                throw new ConflictoException(
                        ("El rol '%s' lo llevan %d usuario(s). Cambialos de rol antes de cerrarlo.")
                                .formatted(rol.getNombre(), usuarios));
            }
            rol.desactivar();
        } else {
            rol.activar();
        }
        return rol;
    }

    /**
     * Borra un rol.
     *
     * <p>Solo si no es de sistema y no lo lleva nadie: un rol borrado con gente
     * dentro dejaria usuarios sin permisos y sin forma de entrar.
     */
    @Transactional
    public void borrar(Long id) {
        Rol rol = obtener(id);

        if (rol.isSistema()) {
            throw new ConflictoException(
                    "El rol '%s' viene de serie y no se puede borrar. Puedes cerrarlo."
                            .formatted(rol.getNombre()));
        }
        long usuarios = rolRepository.contarUsuariosActivos(id);
        if (usuarios > 0) {
            throw new ConflictoException(
                    "El rol '%s' lo llevan %d usuario(s). Cambialos de rol antes de borrarlo."
                            .formatted(rol.getNombre(), usuarios));
        }
        rolRepository.delete(rol);
        log.info("Borrado el rol '{}'", rol.getNombre());
    }

    private void exigirNombreLibre(String nombre, Long idQueSeEdita) {
        if (nombre == null || nombre.isBlank()) {
            return;   // el dominio ya lo rechaza con su propio mensaje
        }
        rolRepository.buscarPorNombre(nombre.trim()).ifPresent(existente -> {
            if (!existente.getId().equals(idQueSeEdita)) {
                throw new ConflictoException("Ya existe un rol llamado '%s'.".formatted(nombre.trim()));
            }
        });
    }
}
