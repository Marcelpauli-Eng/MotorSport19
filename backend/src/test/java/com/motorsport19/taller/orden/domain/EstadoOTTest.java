package com.motorsport19.taller.orden.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la maquina de estados.
 *
 * <p>Se comprueban las transiciones validas una a una y, sobre todo, que
 * <b>ninguna otra</b> lo sea: es facil que un cambio futuro abra un atajo sin
 * querer, y el flujo del taller depende de que no existan.
 */
@DisplayName("Maquina de estados de la orden de trabajo")
class EstadoOTTest {

    @Nested
    @DisplayName("Flujo normal")
    class FlujoNormal {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "RECIBIDA,         EN_DIAGNOSTICO",
                "EN_DIAGNOSTICO,   PRESUPUESTADA",
                "PRESUPUESTADA,    APROBADA",
                "APROBADA,         EN_REPARACION",
                "EN_REPARACION,    LISTA",
                "LISTA,            ENTREGADA"
        })
        @DisplayName("recorre el ciclo completo")
        void transicionesDelCiclo(EstadoOT origen, EstadoOT destino) {
            assertThat(origen.puedeTransitarA(destino)).isTrue();
        }

        @Test
        @DisplayName("desde PRESUPUESTADA el cliente puede rechazar")
        void rechazoDesdePresupuestada() {
            assertThat(EstadoOT.PRESUPUESTADA.puedeTransitarA(EstadoOT.RECHAZADA)).isTrue();
        }

        @Test
        @DisplayName("EN_REPARACION y ESPERANDO_PIEZAS son bidireccionales")
        void bloqueoYReanudacion() {
            assertThat(EstadoOT.EN_REPARACION.puedeTransitarA(EstadoOT.ESPERANDO_PIEZAS)).isTrue();
            assertThat(EstadoOT.ESPERANDO_PIEZAS.puedeTransitarA(EstadoOT.EN_REPARACION)).isTrue();
        }
    }

    @Nested
    @DisplayName("Trabajo ya cerrado con el cliente")
    class TrabajoPreparado {

        @Test
        @DisplayName("de recibida se puede dejar preparada y de preparada al taller")
        void atajoDelTrabajoCerrado() {
            assertThat(EstadoOT.RECIBIDA.puedeTransitarA(EstadoOT.PREPARADA)).isTrue();
            assertThat(EstadoOT.PREPARADA.puedeTransitarA(EstadoOT.EN_REPARACION)).isTrue();
        }

        @Test
        @DisplayName("una preparada se puede anular si el cliente se echa atras")
        void anulacion() {
            assertThat(EstadoOT.PREPARADA.puedeTransitarA(EstadoOT.RECHAZADA)).isTrue();
        }

        @Test
        @DisplayName("no vuelve al camino del presupuesto: el precio ya esta cerrado")
        void noVuelveAlPresupuesto() {
            assertThat(EstadoOT.PREPARADA.puedeTransitarA(EstadoOT.EN_DIAGNOSTICO)).isFalse();
            assertThat(EstadoOT.PREPARADA.puedeTransitarA(EstadoOT.PRESUPUESTADA)).isFalse();
            assertThat(EstadoOT.PREPARADA.puedeTransitarA(EstadoOT.APROBADA)).isFalse();
        }

        @Test
        @DisplayName("tampoco se salta el trabajo: de preparada no se entrega")
        void noSeSaltaElTrabajo() {
            assertThat(EstadoOT.PREPARADA.puedeTransitarA(EstadoOT.LISTA)).isFalse();
            assertThat(EstadoOT.PREPARADA.puedeTransitarA(EstadoOT.ENTREGADA)).isFalse();
        }

        @Test
        @DisplayName("es el estado en el que direccion compone las lineas")
        void admiteComponerElPresupuesto() {
            assertThat(EstadoOT.PREPARADA.permiteEditarLineas()).isTrue();
        }
    }

    @Nested
    @DisplayName("Transiciones prohibidas")
    class Prohibidas {

        @ParameterizedTest(name = "{0} -> {1} no se permite")
        @CsvSource({
                // No se puede saltar el diagnostico ni el presupuesto.
                "RECIBIDA,         PRESUPUESTADA",
                "RECIBIDA,         EN_REPARACION",
                "RECIBIDA,         ENTREGADA",
                "EN_DIAGNOSTICO,   APROBADA",
                "EN_DIAGNOSTICO,   EN_REPARACION",
                // Nadie repara lo que el cliente no ha aprobado.
                "PRESUPUESTADA,    EN_REPARACION",
                "PRESUPUESTADA,    LISTA",
                // No se entrega sin terminar.
                "APROBADA,         LISTA",
                "APROBADA,         ENTREGADA",
                "EN_REPARACION,    ENTREGADA",
                // Solo se rechaza el presupuesto, no una reparacion en curso.
                "EN_REPARACION,    RECHAZADA",
                "APROBADA,         RECHAZADA",
                "RECIBIDA,         RECHAZADA",
                // No se vuelve atras.
                "PRESUPUESTADA,    EN_DIAGNOSTICO",
                "APROBADA,         PRESUPUESTADA",
                "LISTA,            EN_REPARACION"
        })
        @DisplayName("rechaza saltos que romperian el flujo del taller")
        void transicionesInvalidas(EstadoOT origen, EstadoOT destino) {
            assertThat(origen.puedeTransitarA(destino)).isFalse();
        }

        @Test
        @DisplayName("una orden ENTREGADA no admite ningun cambio")
        void entregadaEsTerminal() {
            assertThat(EstadoOT.ENTREGADA.esTerminal()).isTrue();
            for (EstadoOT destino : EstadoOT.values()) {
                assertThat(EstadoOT.ENTREGADA.puedeTransitarA(destino))
                        .as("ENTREGADA no puede pasar a %s", destino)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("una orden RECHAZADA no admite ningun cambio")
        void rechazadaEsTerminal() {
            assertThat(EstadoOT.RECHAZADA.esTerminal()).isTrue();
            for (EstadoOT destino : EstadoOT.values()) {
                assertThat(EstadoOT.RECHAZADA.puedeTransitarA(destino)).isFalse();
            }
        }

        @Test
        @DisplayName("ningun estado puede transitar a si mismo")
        void sinAutotransiciones() {
            for (EstadoOT estado : EstadoOT.values()) {
                assertThat(estado.puedeTransitarA(estado))
                        .as("%s no deberia poder transitar a si mismo", estado)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("el mapa completo de transiciones es exactamente el esperado")
        void mapaCompletoDeTransiciones() {
            // Este test es el que salta si alguien abre un atajo sin querer.
            assertThat(EstadoOT.RECIBIDA.siguientesPosibles())
                    .isEqualTo(EnumSet.of(EstadoOT.EN_DIAGNOSTICO, EstadoOT.PREPARADA));
            assertThat(EstadoOT.PREPARADA.siguientesPosibles())
                    .isEqualTo(EnumSet.of(EstadoOT.EN_REPARACION, EstadoOT.RECHAZADA));
            assertThat(EstadoOT.EN_DIAGNOSTICO.siguientesPosibles())
                    .isEqualTo(EnumSet.of(EstadoOT.PRESUPUESTADA));
            assertThat(EstadoOT.PRESUPUESTADA.siguientesPosibles())
                    .isEqualTo(EnumSet.of(EstadoOT.APROBADA, EstadoOT.RECHAZADA));
            assertThat(EstadoOT.APROBADA.siguientesPosibles())
                    .isEqualTo(EnumSet.of(EstadoOT.EN_REPARACION));
            assertThat(EstadoOT.EN_REPARACION.siguientesPosibles())
                    .isEqualTo(EnumSet.of(EstadoOT.ESPERANDO_PIEZAS, EstadoOT.LISTA));
            assertThat(EstadoOT.ESPERANDO_PIEZAS.siguientesPosibles())
                    .isEqualTo(EnumSet.of(EstadoOT.EN_REPARACION, EstadoOT.LISTA));
            assertThat(EstadoOT.LISTA.siguientesPosibles())
                    .isEqualTo(EnumSet.of(EstadoOT.ENTREGADA));
            assertThat(EstadoOT.ENTREGADA.siguientesPosibles()).isEmpty();
            assertThat(EstadoOT.RECHAZADA.siguientesPosibles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Reglas asociadas al estado")
    class ReglasDeEstado {

        @ParameterizedTest
        @EnumSource(value = EstadoOT.class,
                names = {"PREPARADA", "EN_DIAGNOSTICO", "PRESUPUESTADA", "APROBADA", "EN_REPARACION",
                        "ESPERANDO_PIEZAS"})
        @DisplayName("las lineas se editan mientras se prepara, diagnostica, presupuesta o repara")
        void lineasEditables(EstadoOT estado) {
            assertThat(estado.permiteEditarLineas()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = EstadoOT.class, names = {"RECIBIDA", "LISTA", "ENTREGADA", "RECHAZADA"})
        @DisplayName("las lineas no se tocan antes de diagnosticar ni despues de cerrar")
        void lineasNoEditables(EstadoOT estado) {
            assertThat(estado.permiteEditarLineas()).isFalse();
        }

        @Test
        @DisplayName("solo se factura desde LISTA o ENTREGADA")
        void estadosFacturables() {
            Set<EstadoOT> facturables = EnumSet.noneOf(EstadoOT.class);
            for (EstadoOT estado : EstadoOT.values()) {
                if (estado.permiteFacturar()) {
                    facturables.add(estado);
                }
            }
            assertThat(facturables).isEqualTo(EnumSet.of(EstadoOT.LISTA, EstadoOT.ENTREGADA));
        }
    }

    @Test
    @DisplayName("el error de transicion invalida dice a donde SI se puede ir")
    void mensajeDeErrorUtil() {
        assertThatThrownBy(() -> EstadoOT.RECIBIDA.exigirTransicionA(EstadoOT.ENTREGADA, "OT-2026-00001"))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("OT-2026-00001")
                .hasMessageContaining("RECIBIDA")
                .hasMessageContaining("ENTREGADA")
                // Lo importante: que el usuario sepa que hacer a continuacion.
                .hasMessageContaining("solo puede pasar a: EN_DIAGNOSTICO");
    }

    @Test
    @DisplayName("el error sobre una orden cerrada lo dice sin sugerir alternativas")
    void mensajeSobreOrdenCerrada() {
        assertThatThrownBy(() -> EstadoOT.ENTREGADA.exigirTransicionA(EstadoOT.LISTA, "OT-2026-00001"))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("no admite mas cambios");
    }
}
