package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.usuario.domain.Permiso;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Quien esta haciendo la peticion, y que puede hacer.
 *
 * <p>Antes de la fase 5, los endpoints recibian el usuario en un parametro
 * {@code ?usuarioId=}. Eso significaba que cualquiera podia firmar una operacion
 * como otra persona con solo cambiar un numero en la URL: el historial de una
 * orden o de un movimiento de stock no valia nada. Ahora el usuario sale
 * siempre del token, que va firmado y no se puede falsificar.
 *
 * <p>Las preguntas son por <b>permiso</b> y no por rol. Con los roles clavados
 * en el codigo se preguntaba «¿eres tecnico?» para decidir si tapar los precios;
 * ahora que los roles los compone el administrador, esa pregunta ya no significa
 * nada y la buena es «¿puedes ver importes?».
 */
@Component
public class UsuarioActual {

    /** Usuario autenticado, o vacio si la peticion es anonima. */
    public Optional<UsuarioAutenticado> obtener() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !autenticacion.isAuthenticated()) {
            return Optional.empty();
        }
        if (autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    /** Identificador del usuario autenticado, o {@code null} si no lo hay. */
    public Long id() {
        return obtener().map(UsuarioAutenticado::id).orElse(null);
    }

    public String username() {
        return obtener().map(UsuarioAutenticado::username).orElse(null);
    }

    /** Nombre del rol, solo para enseñarlo. Para decidir se usan los permisos. */
    public String rol() {
        return obtener().map(UsuarioAutenticado::rolNombre).orElse(null);
    }

    public boolean tienePermiso(Permiso permiso) {
        return obtener().map(u -> u.tiene(permiso)).orElse(false);
    }

    /**
     * Si se le tapan los importes.
     *
     * <p>Es la pregunta que sustituye al viejo «¿eres tecnico?»: lo que decide
     * que un usuario no vea precios ya no es su rol sino no tener concedido
     * {@link Permiso#IMPORTES_VER}.
     */
    public boolean sinImportes() {
        return !tienePermiso(Permiso.IMPORTES_VER);
    }

    /**
     * Si solo puede ver y trabajar las ordenes que tiene asignadas.
     *
     * <p>Sustituye al viejo «¿eres tecnico?» del tablero. Es una restriccion de
     * datos y no de rutas: la URL es la misma para todos, pero sin
     * {@link Permiso#ORDENES_VER_TODAS} el listado solo devuelve las suyas.
     */
    public boolean soloVeSusOrdenes() {
        return obtener().isPresent() && !tienePermiso(Permiso.ORDENES_VER_TODAS);
    }
}
