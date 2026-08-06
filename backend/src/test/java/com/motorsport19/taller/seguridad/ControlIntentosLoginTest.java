package com.motorsport19.taller.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del freno a los intentos de adivinar contrasenas.
 *
 * <p>Sin este control, un atacante puede probar contrasenas indefinidamente
 * contra el endpoint de login. BCrypt encarece cada intento, pero no lo impide.
 */
@DisplayName("Control de intentos de acceso")
class ControlIntentosLoginTest {

    private final ControlIntentosLogin control =
            new ControlIntentosLogin(3, Duration.ofMinutes(15));

    @Test
    @DisplayName("un usuario nuevo no esta bloqueado")
    void sinIntentos() {
        assertThat(control.estaBloqueado("mostrador")).isFalse();
    }

    @Test
    @DisplayName("aguanta los fallos hasta el limite")
    void pordebajoDelLimite() {
        control.registrarFallo("mostrador");
        control.registrarFallo("mostrador");

        assertThat(control.estaBloqueado("mostrador")).isFalse();
    }

    @Test
    @DisplayName("bloquea al alcanzar el limite")
    void alcanzaElLimite() {
        control.registrarFallo("mostrador");
        control.registrarFallo("mostrador");
        control.registrarFallo("mostrador");

        assertThat(control.estaBloqueado("mostrador")).isTrue();
        assertThat(control.minutosRestantes("mostrador")).isBetween(1L, 15L);
    }

    @Test
    @DisplayName("un acceso correcto borra el historial de fallos")
    void elExitoLimpia() {
        control.registrarFallo("mostrador");
        control.registrarFallo("mostrador");
        control.registrarExito("mostrador");

        // Si el contador no se reiniciase, un usuario que se equivoca dos veces
        // al mes acabaria bloqueandose sin motivo.
        control.registrarFallo("mostrador");
        control.registrarFallo("mostrador");
        assertThat(control.estaBloqueado("mostrador")).isFalse();
    }

    @Test
    @DisplayName("el bloqueo es por usuario, no arrastra a los demas")
    void bloqueoAislado() {
        control.registrarFallo("mostrador");
        control.registrarFallo("mostrador");
        control.registrarFallo("mostrador");

        // Todo el taller sale a internet por la misma IP: bloquear por IP
        // dejaria fuera a los companeros del que se equivoca.
        assertThat(control.estaBloqueado("mostrador")).isTrue();
        assertThat(control.estaBloqueado("jortega")).isFalse();
    }

    @Test
    @DisplayName("el nombre de usuario no distingue mayusculas")
    void insensibleAMayusculas() {
        control.registrarFallo("Mostrador");
        control.registrarFallo("MOSTRADOR");
        control.registrarFallo("mostrador");

        // Sin esto se esquivaria el bloqueo cambiando la caja del usuario.
        assertThat(control.estaBloqueado("mostrador")).isTrue();
    }
}
