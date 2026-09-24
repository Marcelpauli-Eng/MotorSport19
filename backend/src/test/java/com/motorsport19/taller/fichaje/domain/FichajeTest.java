package com.motorsport19.taller.fichaje.domain;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.support.RolesDePrueba;
import com.motorsport19.taller.usuario.domain.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El registro de jornada.
 *
 * <p>Es obligatorio por ley y son las horas que cobra una persona, asi que lo
 * que se comprueba aqui es sobre todo lo que <b>no</b> puede pasar: que se
 * pierda lo que se fichó, que alguien se invente horas sin firmar, o que una
 * jornada acabe antes de empezar.
 */
@DisplayName("Fichaje de jornada")
class FichajeTest {

    private static Usuario trabajador() {
        Usuario u = Usuario.crear("jortega", "$2a$10$x", "Javier Ortega", null, null,
                RolesDePrueba.taller());
        ReflectionTestUtils.setField(u, "id", 5L);
        return u;
    }

    private static Usuario jefe() {
        Usuario u = Usuario.crear("admin", "$2a$10$x", "Direccion", null, null,
                RolesDePrueba.administracion());
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }

    /** Una jornada cerrada de las horas que se digan, terminada hace poco. */
    private static Fichaje jornadaDe(long horas) {
        Fichaje f = Fichaje.empezar(trabajador());
        ReflectionTestUtils.setField(f, "inicio", Instant.now().minus(horas, ChronoUnit.HOURS));
        f.terminar();
        return f;
    }

    @Nested
    @DisplayName("Empezar y terminar")
    class ElDiaNormal {

        @Test
        @DisplayName("al empezar queda abierta y con la hora de ahora")
        void empezar() {
            Fichaje f = Fichaje.empezar(trabajador());

            assertThat(f.estaAbierta()).isTrue();
            assertThat(f.finReal()).isNull();
            assertThat(f.getInicio()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("una jornada abierta va contando las horas que lleva")
        void cuentaMientrasEstaAbierta() {
            Fichaje f = Fichaje.empezar(trabajador());
            ReflectionTestUtils.setField(f, "inicio", Instant.now().minus(3, ChronoUnit.HOURS));

            assertThat(f.duracion()).isBetween(Duration.ofHours(3), Duration.ofHours(3).plusMinutes(1));
        }

        @Test
        @DisplayName("al terminar se queda con lo que duró")
        void terminar() {
            Fichaje f = jornadaDe(8);

            assertThat(f.estaAbierta()).isFalse();
            assertThat(f.duracion()).isBetween(Duration.ofHours(8), Duration.ofHours(8).plusMinutes(1));
        }

        @Test
        @DisplayName("no se puede cerrar dos veces")
        void cerrarDosVeces() {
            Fichaje f = jornadaDe(8);

            assertThatThrownBy(f::terminar).isInstanceOf(ConflictoException.class);
        }

        @Test
        @DisplayName("un fichaje siempre es de alguien")
        void sinUsuario() {
            assertThatThrownBy(() -> Fichaje.empezar(null))
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    @Nested
    @DisplayName("Se fue sin fichar la salida")
    class OlvidoDeSalida {

        @Test
        @DisplayName("la cierra el jefe con la hora real y queda marcada")
        void cierreManual() {
            Fichaje f = Fichaje.empezar(trabajador());
            ReflectionTestUtils.setField(f, "inicio", Instant.now().minus(20, ChronoUnit.HOURS));
            Instant salidaReal = Instant.now().minus(12, ChronoUnit.HOURS);

            f.cerrarPorOlvido(salidaReal, "Se fue sin fichar", jefe());

            assertThat(f.estaAbierta()).isFalse();
            assertThat(f.isCerradaPorOlvido())
                    .as("tiene que verse distinta: no es una jornada normal")
                    .isTrue();
            assertThat(f.finReal()).isEqualTo(salidaReal);
            assertThat(f.getCorregidoPor().getUsername()).isEqualTo("admin");
        }

        @Test
        @DisplayName("cerrarla exige decir por que")
        void sinMotivo() {
            Fichaje f = Fichaje.empezar(trabajador());

            assertThatThrownBy(() -> f.cerrarPorOlvido(Instant.now(), "  ", jefe()))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("por que");
        }

        @Test
        @DisplayName("la salida no puede ser anterior a la entrada")
        void salidaAntesDeEntrar() {
            Fichaje f = Fichaje.empezar(trabajador());

            assertThatThrownBy(() -> f.cerrarPorOlvido(
                    Instant.now().minus(5, ChronoUnit.HOURS), "x", jefe()))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("ni estar en el futuro")
        void salidaEnElFuturo() {
            Fichaje f = Fichaje.empezar(trabajador());

            assertThatThrownBy(() -> f.cerrarPorOlvido(
                    Instant.now().plus(2, ChronoUnit.HOURS), "x", jefe()))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("futuro");
        }
    }

    @Nested
    @DisplayName("Correcciones")
    class Correcciones {

        @Test
        @DisplayName("lo que se fichó NO se pierde al corregir")
        void noPisaElOriginal() {
            Fichaje f = jornadaDe(8);
            Instant fichadoOriginal = f.getInicio();
            Instant corregido = fichadoOriginal.plus(30, ChronoUnit.MINUTES);

            f.corregir(corregido, null, "Fichó antes de llegar", jefe());

            assertThat(f.getInicio())
                    .as("el original sigue ahi: es lo unico que prueba que se fichó")
                    .isEqualTo(fichadoOriginal);
            assertThat(f.inicioReal()).isEqualTo(corregido);
            assertThat(f.fueCorregida()).isTrue();
        }

        @Test
        @DisplayName("las horas que cuentan pasan a ser las corregidas")
        void cuentaLaCorregida() {
            Fichaje f = jornadaDe(8);

            f.corregir(f.getInicio().plus(4, ChronoUnit.HOURS), null, "Llegó a mediodia", jefe());

            assertThat(f.duracion()).isBetween(Duration.ofHours(4), Duration.ofHours(4).plusMinutes(1));
        }

        @Test
        @DisplayName("corregir exige motivo y queda firmado")
        void firmada() {
            Fichaje f = jornadaDe(8);

            assertThatThrownBy(() -> f.corregir(null, null, "", jefe()))
                    .isInstanceOf(ReglaNegocioException.class);

            f.corregir(null, f.getFin().minus(1, ChronoUnit.HOURS), "Se fue antes", jefe());
            assertThat(f.getMotivoCorreccion()).isEqualTo("Se fue antes");
            assertThat(f.getCorregidoEn()).isNotNull();
        }

        @Test
        @DisplayName("no se corrige una jornada que sigue abierta")
        void abiertaNoSeCorrige() {
            Fichaje f = Fichaje.empezar(trabajador());

            assertThatThrownBy(() -> f.corregir(null, null, "x", jefe()))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("abierta");
        }

        @Test
        @DisplayName("no se puede dejar la salida antes de la entrada")
        void ordenImposible() {
            Fichaje f = jornadaDe(8);

            assertThatThrownBy(() -> f.corregir(
                    f.getFin().plus(1, ChronoUnit.HOURS), null, "x", jefe()))
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }
}
