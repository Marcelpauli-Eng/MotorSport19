package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.usuario.domain.Rol;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sustituye el {@code Jwt} crudo por un {@link UsuarioAutenticado}.
 *
 * <p>Sin esto, el resto de la aplicacion tendria que leer campos sueltos del
 * token («dame el claim uid, conviertelo a Long...») cada vez que necesita saber
 * quien esta operando. Con el filtro, todo el codigo ve siempre el mismo tipo,
 * venga la peticion de donde venga.
 *
 * <p>Se limita a reempaquetar lo que ya viene firmado: no consulta la base de
 * datos ni concede nada que el token no dijera.
 */
@Component
public class FiltroUsuarioAutenticado extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        Authentication actual = SecurityContextHolder.getContext().getAuthentication();

        if (actual != null && actual.getPrincipal() instanceof Jwt token) {
            UsuarioAutenticado usuario = desdeToken(token);
            if (usuario != null) {
                var reemplazo = new UsernamePasswordAuthenticationToken(
                        usuario, actual.getCredentials(), actual.getAuthorities());
                reemplazo.setDetails(actual.getDetails());
                SecurityContextHolder.getContext().setAuthentication(reemplazo);
            }
        }

        cadena.doFilter(peticion, respuesta);
    }

    private UsuarioAutenticado desdeToken(Jwt token) {
        Long id = token.getClaim("uid") instanceof Number numero ? numero.longValue() : null;
        String rolTexto = token.getClaimAsString("rol");
        if (id == null || rolTexto == null) {
            return null;
        }
        try {
            return new UsuarioAutenticado(
                    id,
                    token.getSubject(),
                    token.getClaimAsString("nombre"),
                    Rol.valueOf(rolTexto),
                    null,        // el hash de la contrasena no viaja en el token
                    true);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
