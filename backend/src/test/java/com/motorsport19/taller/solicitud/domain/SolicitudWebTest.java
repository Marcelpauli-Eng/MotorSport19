package com.motorsport19.taller.solicitud.domain;

import com.motorsport19.taller.agenda.domain.Cita;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Solicitud de la web")
class SolicitudWebTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 20);

    static SolicitudWeb solicitud(TipoSolicitud tipo) {
        return SolicitudWeb.recibir("ref-1", tipo, "fr", "  Claire Dubois ", "+33 6 12 34 56 78",
                " ", "Yamaha", "R6", "", "Revision y neumaticos para Magny-Cours", DIA, 2);
    }

    @Nested
    @DisplayName("Llegada")
    class Llegada {

        @Test
        @DisplayName("entra pendiente, con los textos limpios")
        void entraPendiente() {
            SolicitudWeb s = solicitud(TipoSolicitud.CITA);

            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
            assertThat(s.getNombre()).isEqualTo("Claire Dubois");
            assertThat(s.getEmail()).isNull();
            assertThat(s.getMatricula()).isNull();
            assertThat(s.getFechaPreferida()).isEqualTo(DIA);
            assertThat(s.getNumFotos()).isEqualTo((short) 2);
            assertThat(s.getAtendidaEn()).isNull();
        }

        @Test
        @DisplayName("un presupuesto no guarda fecha: no va a un dia concreto")
        void presupuestoSinFecha() {
            assertThat(solicitud(TipoSolicitud.PRESUPUESTO).getFechaPreferida()).isNull();
        }

        @Test
        @DisplayName("la moto se describe como se apuntaria en una cita sin ficha")
        void descripcionMoto() {
            assertThat(solicitud(TipoSolicitud.CITA).descripcionMoto()).isEqualTo("Yamaha R6");
            SolicitudWeb conMatricula = SolicitudWeb.recibir("ref-2", TipoSolicitud.CITA, "es", "Ana",
                    "600100200", null, "Honda", "CBR", "1234ABC", "Frenos", null, 0);
            assertThat(conMatricula.descripcionMoto()).isEqualTo("Honda CBR · 1234ABC");
        }

        @Test
        @DisplayName("sin telefono no hay a quien contestar")
        void sinTelefono() {
            assertThatThrownBy(() -> SolicitudWeb.recibir("ref-3", TipoSolicitud.CITA, "es", "Ana",
                    " ", null, "Honda", "CBR", null, "Frenos", null, 0))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("telefono");
        }

        @Test
        @DisplayName("solo los cuatro idiomas de la web")
        void idiomaDesconocido() {
            assertThatThrownBy(() -> SolicitudWeb.recibir("ref-4", TipoSolicitud.CITA, "de", "Ana",
                    "600100200", null, "Honda", "CBR", null, "Frenos", null, 0))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("como mucho tres fotos")
        void demasiadasFotos() {
            assertThatThrownBy(() -> SolicitudWeb.recibir("ref-5", TipoSolicitud.CITA, "es", "Ana",
                    "600100200", null, "Honda", "CBR", null, "Frenos", null, 4))
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    @Nested
    @DisplayName("Presupuesto")
    class Presupuesto {

        @Test
        @DisplayName("mandarlo la deja presupuestada, abierta, con importe y canal")
        void enviar() {
            SolicitudWeb s = solicitud(TipoSolicitud.PRESUPUESTO);
            s.enviarPresupuesto(new BigDecimal("1450.00"), "  Carenado completo y montaje  ",
                    CanalPresupuesto.WHATSAPP, null);

            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PRESUPUESTADA);
            assertThat(s.getEstado().abierta()).isTrue();
            assertThat(s.getPresupuestoImporte()).isEqualByComparingTo("1450");
            assertThat(s.getPresupuestoDetalle()).isEqualTo("Carenado completo y montaje");
            assertThat(s.getPresupuestoCanal()).isEqualTo(CanalPresupuesto.WHATSAPP);
            assertThat(s.getPresupuestadaEn()).isNotNull();
            assertThat(s.getAtendidaEn()).isNull();
        }

        @Test
        @DisplayName("si el cliente acepta, se le da cita desde la presupuestada")
        void aceptado() {
            SolicitudWeb s = solicitud(TipoSolicitud.PRESUPUESTO);
            s.enviarPresupuesto(new BigDecimal("180"), null, CanalPresupuesto.WHATSAPP, null);
            Cita cita = Cita.agendar(Instant.now().plus(2, ChronoUnit.DAYS), BigDecimal.ONE, null, null,
                    s.getNombre(), s.getTelefono(), s.descripcionMoto(), s.getNecesita(), null, null, null);

            s.darCita(cita, null);

            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.ATENDIDA);
            assertThat(s.getPresupuestoImporte()).isEqualByComparingTo("180");
        }

        @Test
        @DisplayName("se puede volver a mandar corregido: queda el ultimo")
        void corregido() {
            SolicitudWeb s = solicitud(TipoSolicitud.PRESUPUESTO);
            s.enviarPresupuesto(new BigDecimal("200"), null, CanalPresupuesto.WHATSAPP, null);
            s.enviarPresupuesto(new BigDecimal("180"), "Con descuento", CanalPresupuesto.WHATSAPP, null);

            assertThat(s.getPresupuestoImporte()).isEqualByComparingTo("180");
            assertThat(s.getPresupuestoDetalle()).isEqualTo("Con descuento");
        }

        @Test
        @DisplayName("sin importe, sin email para mandarlo por email, o ya cerrada: no")
        void noValidos() {
            SolicitudWeb s = solicitud(TipoSolicitud.PRESUPUESTO); // llego sin email
            assertThatThrownBy(() -> s.enviarPresupuesto(BigDecimal.ZERO, null, CanalPresupuesto.WHATSAPP, null))
                    .isInstanceOf(ReglaNegocioException.class);
            assertThatThrownBy(() -> s.enviarPresupuesto(BigDecimal.TEN, null, CanalPresupuesto.EMAIL, null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("WhatsApp");
            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);

            s.descartar(null, null);
            assertThatThrownBy(() -> s.enviarPresupuesto(BigDecimal.TEN, null, CanalPresupuesto.WHATSAPP, null))
                    .isInstanceOf(ConflictoException.class);
        }
    }

    @Nested
    @DisplayName("Cierre")
    class Cierre {

        @Test
        @DisplayName("al darle cita queda atendida y enlazada a la cita")
        void darCita() {
            SolicitudWeb s = solicitud(TipoSolicitud.CITA);
            Cita cita = Cita.agendar(Instant.now().plus(1, ChronoUnit.DAYS), new BigDecimal("2"), null, null,
                    s.getNombre(), s.getTelefono(), s.descripcionMoto(), s.getNecesita(), null, null, null);

            s.darCita(cita, null);

            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.ATENDIDA);
            assertThat(s.getCita()).isSameAs(cita);
            assertThat(s.getAtendidaEn()).isNotNull();
        }

        @Test
        @DisplayName("se puede cerrar con una nota, o descartar")
        void atenderYDescartar() {
            SolicitudWeb atendida = solicitud(TipoSolicitud.PRESUPUESTO);
            atendida.marcarAtendida("  Presupuesto por telefono: 180 €  ", null);
            assertThat(atendida.getEstado()).isEqualTo(EstadoSolicitud.ATENDIDA);
            assertThat(atendida.getNota()).isEqualTo("Presupuesto por telefono: 180 €");

            SolicitudWeb descartada = solicitud(TipoSolicitud.CITA);
            descartada.descartar(null, null);
            assertThat(descartada.getEstado()).isEqualTo(EstadoSolicitud.DESCARTADA);
        }

        @Test
        @DisplayName("una solicitud cerrada no se vuelve a cerrar: avisa al segundo puesto")
        void noSeCierraDosVeces() {
            SolicitudWeb s = solicitud(TipoSolicitud.CITA);
            s.marcarAtendida(null, null);

            assertThatThrownBy(() -> s.descartar(null, null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("atendida");
            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.ATENDIDA);
        }

        @Test
        @DisplayName("la nota tiene tope")
        void notaLarga() {
            SolicitudWeb s = solicitud(TipoSolicitud.CITA);
            assertThatThrownBy(() -> s.marcarAtendida("x".repeat(501), null))
                    .isInstanceOf(ReglaNegocioException.class);
            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        }
    }

    @Nested
    @DisplayName("Presupuesto con lineas")
    class PresupuestoConLineas {

        private final BigDecimal veintiuno = new BigDecimal("21.00");

        private SolicitudWeb empezado() {
            SolicitudWeb s = solicitud(TipoSolicitud.PRESUPUESTO);
            s.empezarPresupuesto(new BigDecimal("46.00"));
            return s;
        }

        @Test
        @DisplayName("la mano de obra se valora al precio de la hora del presupuesto, y lo sigue si cambia")
        void manoDeObraALaTarifa() {
            SolicitudWeb s = empezado();
            var linea = s.anadirManoDeObra("Cambio de ruedas", new BigDecimal("1.5"), null, "GENERAL", veintiuno);

            assertThat(linea.getPrecioUnitario()).isEqualByComparingTo("46.00");
            assertThat(linea.getNumeroLinea()).isEqualTo(1);

            s.cambiarTarifaHora(new BigDecimal("50"));
            assertThat(linea.getPrecioUnitario()).isEqualByComparingTo("50");
        }

        @Test
        @DisplayName("empezarlo dos veces no cambia el precio de la hora que ya tenia")
        void tarifaCongelada() {
            SolicitudWeb s = empezado();
            s.empezarPresupuesto(new BigDecimal("99"));
            assertThat(s.getTarifaHora()).isEqualByComparingTo("46.00");
        }

        @Test
        @DisplayName("sin empezar, o sin lineas, no se monta ni se manda")
        void sinEmpezarOSinLineas() {
            SolicitudWeb s = solicitud(TipoSolicitud.PRESUPUESTO);
            assertThatThrownBy(() -> s.anadirManoDeObra("X", BigDecimal.ONE, null, "GENERAL", veintiuno))
                    .isInstanceOf(ConflictoException.class);
            assertThatThrownBy(() -> s.enviarPresupuesto(CanalPresupuesto.WHATSAPP, null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("ninguna linea");
        }

        @Test
        @DisplayName("mandado ya no se toca; reescrito vuelve a pendiente con sus lineas")
        void reescribir() {
            SolicitudWeb s = empezado();
            s.anadirManoDeObra("Cambio de ruedas", BigDecimal.ONE, null, "GENERAL", veintiuno);
            s.enviarPresupuesto(new BigDecimal("55.66"), null, CanalPresupuesto.WHATSAPP, null);

            assertThat(s.permiteEditarLineas()).isFalse();
            assertThatThrownBy(() -> s.anadirManoDeObra("Mas", BigDecimal.ONE, null, "GENERAL", veintiuno))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("Reescribelo");

            s.reescribirPresupuesto();
            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
            assertThat(s.getLineas()).hasSize(1);
            s.anadirManoDeObra("Mas", BigDecimal.ONE, null, "GENERAL", veintiuno);
            assertThat(s.getLineas()).hasSize(2);
        }

        @Test
        @DisplayName("solo se acepta lo que se ha mandado; rechazado queda descartado con el motivo")
        void aceptarYRechazar() {
            SolicitudWeb s = empezado();
            assertThatThrownBy(() -> s.aceptarPresupuesto(null, null)).isInstanceOf(ConflictoException.class);
            assertThatThrownBy(() -> s.reescribirPresupuesto()).isInstanceOf(ConflictoException.class);

            s.rechazarPresupuesto("  Muy caro ", null);
            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.DESCARTADA);
            assertThat(s.getNota()).isEqualTo("Presupuesto rechazado: Muy caro");
        }
    }
}
