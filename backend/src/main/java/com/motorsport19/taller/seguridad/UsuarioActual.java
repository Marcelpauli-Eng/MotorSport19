package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.usuario.domain.Rol;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Quien esta haciendo la peticion.
 *
 * <p>Antes de la fase 5, los endpoints recibian el usuario en un parametro
 * {@code ?usuarioId=}. Eso significaba que cualquiera podia firmar una operacion
 * como otra persona con solo cambiar un numero en la URL: el historial de una
 * orden o de un movimiento de stock no valia nada. Ahora el usuario sale
 * siempre del token, que va firmado y no se puede falsificar.
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

    public Rol rol() {
        return obtener().map(UsuarioAutenticado::rol).orElse(null);
    }

    public boolean esAdmin() {
        return rol() == Rol.ADMIN;
    }

    public boolean esTecnico() {
        return rol() == Rol.TECNICO;
    }

    public boolean esMostrador() {
        return rol() == Rol.MOSTRADOR;
    }
}
