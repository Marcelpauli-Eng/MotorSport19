package com.motorsport19.taller.fichaje;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.motorsport19.taller.common.error.RespuestaError;
import com.motorsport19.taller.fichaje.repository.FichajeRepository;
import com.motorsport19.taller.seguridad.UsuarioAutenticado;
import com.motorsport19.taller.usuario.domain.Permiso;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Sin jornada empezada no se usa el programa.
 *
 * <p>Es la regla del taller: quien entra a trabajar ficha primero. Aqui se
 * cumple de verdad y no solo escondiendo un boton, porque una pantalla se puede
 * saltar escribiendo una direccion y lo que hay detras es el registro de horas
 * de una persona.
 *
 * <p>Se libra quien tenga {@link Permiso#FICHAJE_EXENTO} en su rol, que es como
 * la direccion entra sin fichar. Va por permiso y no clavado a un rol concreto
 * porque los roles los compone el taller: manana puede haber un encargado que
 * tampoco fiche, y eso se resuelve marcando una casilla.
 *
 * <p><b>Lo que nunca se bloquea</b>, o el trabajador se quedaria encerrado
 * fuera sin manera de entrar:
 *
 * <ul>
 *   <li>Identificarse y saber quien es uno.</li>
 *   <li>El propio fichaje: es justo lo que tiene que poder hacer.</li>
 *   <li>Sus propias horas: verlas es un derecho suyo, no un premio por fichar.</li>
 * </ul>
 *
 * <p>Se responde <b>423</b> y no 403. Un 403 dice «no puedes», y aqui si puede:
 * lo que pasa es que falta un paso. La pantalla distingue los dos casos por ese
 * codigo y enseña el boton de empezar jornada en vez de un error.
 */
public class FiltroJornadaAbierta extends OncePerRequestFilter {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    /** 423 Locked: la puerta esta cerrada, pero el usuario tiene la llave. */
    public static final int SIN_FICHAR = 423;

    /** Rutas que siguen abiertas aunque no se haya fichado. */
    private static final Set<String> SIEMPRE_PERMITIDAS = Set.of(
            "/api/auth/login",
            "/api/auth/yo",
            "/api/auth/password",
            "/api/fichajes/jornada",
            "/api/fichajes/mias",
            // Solo dice «algo ha cambiado», sin datos.
            "/api/eventos",
            "/api/actuator/health");

    private final FichajeRepository fichajes;

    public FiltroJornadaAbierta(FichajeRepository fichajes) {
        this.fichajes = fichajes;
    }

    @Override
    @Transactional(readOnly = true)
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        Authentication actual = SecurityContextHolder.getContext().getAuthentication();

        // Sin identificar todavia, o exento por su rol: no es asunto suyo.
        if (actual == null || !(actual.getPrincipal() instanceof UsuarioAutenticado usuario)
                || usuario.tiene(Permiso.FICHAJE_EXENTO)
                || estaPermitidaSiempre(peticion)) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        if (fichajes.tieneJornadaAbierta(usuario.id())) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        respuesta.setStatus(SIN_FICHAR);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        JSON.writeValue(respuesta.getOutputStream(), RespuestaError.de(
                SIN_FICHAR,
                "Jornada sin empezar",
                "Pulse «Empezar jornada» para comenzar a trabajar.",
                peticion.getRequestURI()));
    }

    private boolean estaPermitidaSiempre(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();
        return SIEMPRE_PERMITIDAS.contains(ruta) || "OPTIONS".equals(peticion.getMethod());
    }
}
