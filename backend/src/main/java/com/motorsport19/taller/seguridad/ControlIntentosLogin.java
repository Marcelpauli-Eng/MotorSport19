package com.motorsport19.taller.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno a los intentos de adivinar contrasenas.
 *
 * <p>Sin esto, un atacante puede probar miles de contrasenas por minuto contra
 * el endpoint de login. BCrypt hace cada intento caro, pero no lo impide.
 *
 * <p>Se bloquea por usuario y no por IP a proposito: bloquear por IP deja fuera
 * a todo el taller, que sale a internet por la misma direccion, mientras que un
 * atacante con muchas IP lo esquivaria igualmente.
 *
 * <p>El recuento vive en memoria. Con una sola instancia de la aplicacion, que
 * es el caso tanto en el taller como en Render, es suficiente; reiniciar borra
 * los bloqueos, cosa aceptable.
 */
@Component
public class ControlIntentosLogin {

    private static final Logger log = LoggerFactory.getLogger(ControlIntentosLogin.class);

    private final int maximoIntentos;
    private final Duration bloqueo;
    private final Map<String, Registro> intentos = new ConcurrentHashMap<>();

    public ControlIntentosLogin(
            @Value("${motorsport19.seguridad.max-intentos-login:5}") int maximoIntentos,
            @Value("${motorsport19.seguridad.bloqueo-login:PT15M}") Duration bloqueo) {
        this.maximoIntentos = maximoIntentos;
        this.bloqueo = bloqueo;
    }

    /** Indica si el usuario esta bloqueado ahora mismo. */
    public boolean estaBloqueado(String username) {
        Registro registro = intentos.get(clave(username));
        if (registro == null) {
            return false;
        }
        if (registro.bloqueadoHasta != null && Instant.now().isBefore(registro.bloqueadoHasta)) {
            return true;
        }
        // El bloqueo ha vencido: se olvida y se empieza de cero.
        if (registro.bloqueadoHasta != null) {
            intentos.remove(clave(username));
        }
        return false;
    }

    /** Minutos que quedan de bloqueo, para poder decirselo al usuario. */
    public long minutosRestantes(String username) {
        Registro registro = intentos.get(clave(username));
        if (registro == null || registro.bloqueadoHasta == null) {
            return 0;
        }
        long segundos = Duration.between(Instant.now(), registro.bloqueadoHasta).toSeconds();
        return Math.max(1, (segundos + 59) / 60);
    }

    public void registrarFallo(String username) {
        Registro registro = intentos.computeIfAbsent(clave(username), k -> new Registro());
        registro.fallos++;

        if (registro.fallos >= maximoIntentos) {
            registro.bloqueadoHasta = Instant.now().plus(bloqueo);
            log.warn("Usuario '{}' bloqueado tras {} intentos fallidos. Se desbloquea en {} minutos.",
                    username, registro.fallos, bloqueo.toMinutes());
        }
    }

    /** Un acceso correcto borra el historial de fallos. */
    public void registrarExito(String username) {
        intentos.remove(clave(username));
    }

    private String clave(String username) {
        return username == null ? "" : username.toLowerCase();
    }

    private static final class Registro {
        private int fallos;
        private Instant bloqueadoHasta;
    }
}
