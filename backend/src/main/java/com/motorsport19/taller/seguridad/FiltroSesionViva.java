package com.motorsport19.taller.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.motorsport19.taller.common.error.RespuestaError;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Comprueba en cada peticion que quien la hace sigue siendo quien dice el token.
 *
 * <p>El token va firmado y lleva dentro el usuario y sus permisos, asi que
 * autorizar no costaba ninguna consulta. El problema es que un token dura ocho
 * horas y en ese rato la realidad cambia:
 *
 * <ul>
 *   <li>Se da de baja a un empleado que se va del taller. Con la sesion ya
 *       abierta seguia entrando, listando y <b>creando datos</b> el resto del
 *       dia: solo se le cerraba la puerta de volver a entrar.</li>
 *   <li>Se le quita un permiso a un rol —«que este ya no facture»— y quien
 *       estaba dentro seguia facturando hasta que cerraba sesion.</li>
 * </ul>
 *
 * <p>Las dos cosas se piden en caliente, delante del empleado, y quien las pide
 * espera que surtan efecto ahora, no cuando caduque un token que no ve.
 *
 * <p>Por eso este filtro va <b>dentro</b> de la cadena de seguridad y antes de
 * que se decidan los permisos de la ruta: mira la ficha del usuario en la base
 * de datos y, si sigue activo, rehace la autenticacion con los permisos que su
 * rol tiene <i>ahora</i>. Si ya no esta o esta de baja, corta con un 401 y la
 * aplicacion le manda a la pantalla de entrada.
 *
 * <p>Cuesta una consulta por peticion, por clave primaria y con el rol en el
 * mismo viaje. En un taller con cinco personas eso no se nota; que un despido no
 * tenga efecto hasta la noche, si.
 */
public class FiltroSesionViva extends OncePerRequestFilter {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private final UsuarioRepository usuarioRepository;

    public FiltroSesionViva(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        Authentication actual = SecurityContextHolder.getContext().getAuthentication();

        // Sin token todavia (login, PDF publico, preflight...): no hay nada que
        // revalidar y del acceso ya se encarga la configuracion de rutas.
        if (actual == null || !(actual.getPrincipal() instanceof Jwt token)) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        Optional<Usuario> ficha = idDelToken(token).flatMap(usuarioRepository::findById);

        if (ficha.isEmpty() || !ficha.get().isActivo()) {
            SecurityContextHolder.clearContext();
            rechazar(peticion, respuesta);
            return;
        }

        // Los permisos salen de la ficha, no del token: si el administrador ha
        // retocado el rol hace un minuto, manda lo de ahora.
        UsuarioAutenticado usuario = UsuarioAutenticado.de(ficha.get());
        var reemplazo = new UsernamePasswordAuthenticationToken(
                usuario, actual.getCredentials(), usuario.getAuthorities());
        reemplazo.setDetails(actual.getDetails());
        SecurityContextHolder.getContext().setAuthentication(reemplazo);

        cadena.doFilter(peticion, respuesta);
    }

    private Optional<Long> idDelToken(Jwt token) {
        return token.getClaim("uid") instanceof Number numero
                ? Optional.of(numero.longValue())
                : Optional.empty();
    }

    private void rechazar(HttpServletRequest peticion, HttpServletResponse respuesta) throws IOException {
        respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        JSON.writeValue(respuesta.getOutputStream(), RespuestaError.de(
                HttpStatus.UNAUTHORIZED.value(),
                "Sesion cerrada",
                "Su cuenta ya no esta activa. Consulte con el administrador del taller.",
                peticion.getRequestURI()));
    }
}
