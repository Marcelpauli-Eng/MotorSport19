package com.motorsport19.taller.agenda.domain;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.support.OrdenesDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cita")
class CitaTest {

    private static final Instant MANANA = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final BigDecimal DOS_HORAS = new BigDecimal("2.00");

    private static Moto motoDePrueba() {
        Cliente cliente = OrdenesDePrueba.cliente();
        return OrdenesDePrueba.moto(cliente);
    }

    private static Cita conMoto() {
        Moto moto = motoDePrueba();
        return Cita.agendar(MANANA, DOS_HORAS, moto, moto.getCliente(), null, null,
                null, "Revision de los 20.000 km", null, null);
    }

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("una cita con moto del sistema toma de ella el contacto")
        void citaConMoto() {
            Cita cita = conMoto();

            assertThat(cita.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
            assertThat(cita.nombreDeContacto()).isEqualTo("Carlos Nunez Prieto");
            assertThat(cita.telefonoDeContacto()).isEqualTo("600100101");
            assertThat(cita.moto()).contains("MT-07");
        }

        @Test
        @DisplayName("se puede citar a alguien que aun no esta dado de alta")
        void citaSinMoto() {
            Cita cita = Cita.agendar(MANANA, DOS_HORAS, null, null, "Alberto Ruiz", "655443322",
                    "Triumph Street Triple", "Quiere presupuesto de revision", null, null);

            assertThat(cita.getMoto()).isNull();
            assertThat(cita.nombreDeContacto()).isEqualTo("Alberto Ruiz");
            assertThat(cita.moto()).isEqualTo("Triumph Street Triple");
        }

        @Test
        @DisplayName("sin moto y sin contacto no hay cita: seria un hueco de nadie")
        void citaSinMotoNiContacto() {
            assertThatThrownBy(() -> Cita.agendar(MANANA, DOS_HORAS, null, null, null, null,
                    "Una moto roja", "Revision", null, null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("nombre y telefono");
        }

        @Test
        @DisplayName("hay que decir a que viene la moto")
        void motivoObligatorio() {
            assertThatThrownBy(() -> {
                Moto m = motoDePrueba();
                Cita.agendar(MANANA, DOS_HORAS, m, m.getCliente(), null, null,
                    null, "   ", null, null);
            })
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("la duracion tiene que ser positiva y caber en un dia")
        void duracionRazonable() {
            assertThatThrownBy(() -> {
                Moto m = motoDePrueba();
                Cita.agendar(MANANA, BigDecimal.ZERO, m, m.getCliente(), null,
                    null, null, "Revision", null, null);
            })
                    .isInstanceOf(ReglaNegocioException.class);

            assertThatThrownBy(() -> {
                Moto m = motoDePrueba();
                Cita.agendar(MANANA, new BigDecimal("30"), m, m.getCliente(),
                    null, null, null, "Revision", null, null);
            })
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("24 horas");
        }
    }

    @Nested
    @DisplayName("Ciclo de vida")
    class CicloDeVida {

        @Test
        @DisplayName("solo cuentan para la carga del dia las citas vivas")
        void soloLasVivasOcupanAgenda() {
            Cita cita = conMoto();
            assertThat(cita.ocupaAgenda()).isTrue();

            cita.confirmar();
            assertThat(cita.ocupaAgenda()).isTrue();

            cita.cancelar("El cliente lo deja para el mes que viene");
            assertThat(cita.ocupaAgenda()).isFalse();
        }

        @Test
        @DisplayName("atender la cita la enlaza con su orden de trabajo")
        void atenderEnlazaLaOrden() {
            Cita cita = conMoto();
            cita.confirmar();
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();

            cita.atender(orden);

            assertThat(cita.getEstado()).isEqualTo(EstadoCita.ATENDIDA);
            assertThat(cita.getOrdenTrabajo()).isSameAs(orden);
            assertThat(cita.ocupaAgenda()).isFalse();
        }

        @Test
        @DisplayName("no se da por atendida una cita sin abrir su orden")
        void atenderExigeOrden() {
            Cita cita = conMoto();

            assertThatThrownBy(() -> cita.atender(null))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("una cita cerrada ya no se mueve ni se cambia")
        void citaCerradaNoSeToca() {
            Cita cita = conMoto();
            cita.cancelar("Se la lleva a otro taller");

            assertThatThrownBy(() -> cita.reprogramar(MANANA.plus(1, ChronoUnit.DAYS)))
                    .isInstanceOf(ConflictoException.class);
            assertThatThrownBy(cita::confirmar)
                    .isInstanceOf(ConflictoException.class);
        }

        @Test
        @DisplayName("no presentarse es distinto de cancelar: el hueco se perdio")
        void noPresentado() {
            Cita cita = conMoto();
            cita.confirmar();

            cita.marcarNoPresentado("No aparecio ni aviso");

            assertThat(cita.getEstado()).isEqualTo(EstadoCita.NO_PRESENTADO);
            assertThat(cita.getMotivoCancelacion()).isEqualTo("No aparecio ni aviso");
        }

        @Test
        @DisplayName("mover la cita de dia no toca el resto de la ficha")
        void reprogramar() {
            Cita cita = conMoto();
            Instant nueva = MANANA.plus(3, ChronoUnit.DAYS);

            cita.reprogramar(nueva);

            assertThat(cita.getFechaHora()).isEqualTo(nueva);
            assertThat(cita.getMotivo()).isEqualTo("Revision de los 20.000 km");
            assertThat(cita.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        }
    }
}
