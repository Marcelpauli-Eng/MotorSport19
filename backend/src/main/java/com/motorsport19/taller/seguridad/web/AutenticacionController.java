package com.motorsport19.taller.seguridad.web;

import com.motorsport19.taller.common.error.RespuestaError;
import com.motorsport19.taller.seguridad.ControlIntentosLogin;
import com.motorsport19.taller.seguridad.ServicioJwt;
import com.motorsport19.taller.seguridad.UsuarioActual;
import com.motorsport19.taller.usuario.domain.Permiso;
import com.motorsport19.taller.seguridad.UsuarioAutenticado;
import com.motorsport19.taller.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Inicio de sesion y datos del usuario en curso.
 */
@RestController
@RequestMapping("/auth")
public class AutenticacionController {

    private static final Logger log = LoggerFactory.getLogger(AutenticacionController.class);

    private final AuthenticationManager gestorAutenticacion;
    private final ServicioJwt servicioJwt;
    private final ControlIntentosLogin controlIntentos;
    private final UsuarioActual usuarioActual;
    private final UsuarioService usuarioService;

    public AutenticacionController(AuthenticationManager gestorAutenticacion,
                                   ServicioJwt servicioJwt,
                                   ControlIntentosLogin controlIntentos,
                                   UsuarioActual usuarioActual,
                                   UsuarioService usuarioService) {
        this.gestorAutenticacion = gestorAutenticacion;
        this.servicioJwt = servicioJwt;
        this.controlIntentos = controlIntentos;
        this.usuarioActual = usuarioActual;
        this.usuarioService = usuarioService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody PeticionLogin peticion) {
        String username = peticion.username().trim();

        if (controlIntentos.estaBloqueado(username)) {
            long minutos = controlIntentos.minutosRestantes(username);
            // 429 y no 422: esto no es un dato mal escrito, es un limite de
            // intentos. Con el codigo correcto, cualquier vigilancia que se
            // ponga delante puede distinguir un ataque de un error del usuario.
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(minutos * 60))
                    .body(RespuestaError.de(HttpStatus.TOO_MANY_REQUESTS.value(), "Demasiados intentos",
                            "Demasiados intentos fallidos. Vuelva a probar dentro de %d minuto(s)."
                                    .formatted(minutos), "/auth/login"));
        }

        try {
            Authentication autenticacion = gestorAutenticacion.authenticate(
                    new UsernamePasswordAuthenticationToken(username, peticion.password()));

            UsuarioAutenticado usuario = (UsuarioAutenticado) autenticacion.getPrincipal();
            controlIntentos.registrarExito(username);
            usuarioService.registrarAcceso(usuario.id());

            ServicioJwt.TokenEmitido token = servicioJwt.emitir(usuario);
            log.info("Acceso correcto de '{}' ({})", usuario.username(), usuario.rolNombre());

            return ResponseEntity.ok(new RespuestaLogin(
                    token.token(),
                    token.caduca(),
                    token.duracionSegundos(),
                    new UsuarioSesion(usuario.id(), usuario.username(), usuario.nombreCompleto(),
                            usuario.rolId(), usuario.rolNombre(), usuario.permisos())));

        } catch (AuthenticationException e) {
            controlIntentos.registrarFallo(username);
            log.warn("Intento de acceso fallido para '{}'", username);
            // Mismo mensaje tanto si el usuario no existe como si la contrasena
            // es incorrecta: distinguirlos permitiria averiguar quien trabaja aqui.
            // El cuerpo hace falta: sin el, la pantalla de entrada no tiene nada
            // que ensenar y tiene que inventarse un texto.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(RespuestaError.de(HttpStatus.UNAUTHORIZED.value(), "Credenciales incorrectas",
                            "Usuario o contrasena incorrectos.", "/auth/login"));
        }
    }

    /**
     * Cambio de contrasena por el propio usuario.
     *
     * <p>Exige la actual: si alguien se deja la sesion abierta en el mostrador,
     * no puede aprovecharse para apropiarse de la cuenta.
     */
    @PostMapping("/password")
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody CambioPassword peticion) {
        Long id = usuarioActual.id();
        if (id == null) {
            throw new IllegalStateException("Sin usuario autenticado");
        }
        usuarioService.cambiarPassword(id, peticion.passwordActual(), peticion.passwordNueva());
        return ResponseEntity.noContent().build();
    }

    /** Datos del usuario en curso. Lo usa el frontend al recargar la pagina. */
    @GetMapping("/yo")
    public UsuarioSesion yo() {
        UsuarioAutenticado usuario = usuarioActual.obtener()
                .orElseThrow(() -> new IllegalStateException("Sin usuario autenticado"));
        return new UsuarioSesion(usuario.id(), usuario.username(), usuario.nombreCompleto(),
                usuario.rolId(), usuario.rolNombre(), usuario.permisos());
    }

    // ------------------------------------------------------------------

    public record PeticionLogin(
            @NotBlank(message = "El usuario es obligatorio") String username,
            @NotBlank(message = "La contrasena es obligatoria") String password) {
    }

    /**
     * @param caduca instante exacto de caducidad, para que el frontend pueda
     *               avisar antes de que la sesion se pierda a media faena
     */
    public record RespuestaLogin(String token, Instant caduca, long duracionSegundos,
                                 UsuarioSesion usuario) {
    }

    /**
     * Quien ha entrado y que puede hacer.
     *
     * <p>Los permisos viajan a la pantalla para que no ofrezca botones que la
     * API va a rechazar. Quien manda sigue siendo el servidor: esto solo evita
     * enseñar puertas cerradas.
     */
    public record UsuarioSesion(Long id, String username, String nombreCompleto, Long rolId,
                                String rol, java.util.Set<Permiso> permisos) {
    }

    public record CambioPassword(
            @NotBlank(message = "La contrasena actual es obligatoria") String passwordActual,
            @NotBlank(message = "La contrasena nueva es obligatoria") String passwordNueva) {
    }
}
