package com.motorsport19.taller.orden.domain;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.support.OrdenesDePrueba;
import com.motorsport19.taller.support.PiezasDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Orden de trabajo")
class OrdenTrabajoTest {

    @Nested
    @DisplayName("Apertura")
    class Apertura {

        @Test
        @DisplayName("nace en RECIBIDA y deja constancia en el historial")
        void naceRecibida() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();

            assertThat(orden.getEstado()).isEqualTo(EstadoOT.RECIBIDA);
            assertThat(orden.getHistorialEstados()).hasSize(1);
            assertThat(orden.getHistorialEstados().get(0).getEstadoAnterior()).isNull();
            assertThat(orden.getHistorialEstados().get(0).getEstadoNuevo()).isEqualTo(EstadoOT.RECIBIDA);
        }

        @Test
        @DisplayName("congela la tarifa por hora del taller")
        void congelaLaTarifa() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();

            // Si manana sube la tarifa del taller, esta OT mantiene la pactada.
            assertThat(orden.getTarifaHora()).isEqualByComparingTo("45.00");
        }

        @Test
        @DisplayName("exige describir el problema del cliente")
        void exigeProblema() {
            var moto = OrdenesDePrueba.moto(OrdenesDePrueba.cliente());

            assertThatThrownBy(() -> OrdenTrabajo.abrir(2026, 1, moto, new BigDecimal("45.00"),
                    100, "   ", null, null, null, null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("problema");
        }

        @Test
        @DisplayName("no se abre para una moto dada de baja")
        void motoDeBaja() {
            var moto = OrdenesDePrueba.moto(OrdenesDePrueba.cliente());
            moto.darDeBaja();

            assertThatThrownBy(() -> OrdenTrabajo.abrir(2026, 1, moto, new BigDecimal("45.00"),
                    100, "Averia", null, null, null, null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("dada de baja");
        }
    }

    @Nested
    @DisplayName("Presupuesto")
    class Presupuesto {

        @Test
        @DisplayName("no se presupuesta sin diagnostico")
        void sinDiagnostico() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            orden.iniciarDiagnostico(null, null);

            assertThatThrownBy(() -> orden.presupuestar(null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("diagnostico");
        }

        @Test
        @DisplayName("no se presupuesta sin ninguna linea")
        void sinLineas() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            orden.iniciarDiagnostico(null, null);
            orden.registrarDiagnostico("Junta de culata danada");

            // El cliente no puede aprobar lo que no ve.
            assertThatThrownBy(() -> orden.presupuestar(null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("sin ninguna linea");
        }

        @Test
        @DisplayName("registra la fecha al presupuestar y al aprobar")
        void registraFechas() {
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon();

            assertThat(orden.getEstado()).isEqualTo(EstadoOT.APROBADA);
            assertThat(orden.getFechaPresupuesto()).isNotNull();
            assertThat(orden.getFechaAprobacion()).isNotNull();
            assertThat(orden.getAprobadoPor()).isEqualTo("Carlos Nunez Prieto");
        }

        @Test
        @DisplayName("el rechazo exige motivo y cierra la orden")
        void rechazo() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            orden.iniciarDiagnostico(null, null);
            orden.registrarDiagnostico("Junta de culata danada");
            orden.anadirManoDeObra("Desmontaje de culata", new BigDecimal("8"), BigDecimal.ZERO,
                    "GENERAL", new BigDecimal("21.00"));
            orden.presupuestar(null);

            assertThatThrownBy(() -> orden.rechazar("   ", null))
                    .isInstanceOf(ReglaNegocioException.class);

            orden.rechazar("El cliente prefiere pedir una segunda opinion", null);

            assertThat(orden.getEstado()).isEqualTo(EstadoOT.RECHAZADA);
            assertThat(orden.getMotivoRechazo()).contains("segunda opinion");
            // La moto sale del taller sin reparar.
            assertThat(orden.getFechaRealSalida()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Lineas")
    class Lineas {

        @Test
        @DisplayName("la mano de obra se valora a la tarifa congelada de la orden")
        void manoDeObraUsaLaTarifaDeLaOrden() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            orden.iniciarDiagnostico(null, null);

            LineaOT linea = orden.anadirManoDeObra("Revision", new BigDecimal("2.5"), BigDecimal.ZERO,
                    "GENERAL", new BigDecimal("21.00"));

            assertThat(linea.getPrecioUnitario()).isEqualByComparingTo("45.00");
            assertThat(linea.getTipo()).isEqualTo(TipoLinea.MANO_DE_OBRA);
            assertThat(linea.getPieza()).isNull();
        }

        @Test
        @DisplayName("el precio de la pieza se congela al anadirla al presupuesto")
        void precioDePiezaCongelado() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            orden.iniciarDiagnostico(null, null);
            Pieza pieza = PiezasDePrueba.conStock(1L, "KIT-TRA-525", "4");

            LineaOT linea = orden.anadirPieza(pieza, BigDecimal.ONE, BigDecimal.ZERO,
                    new BigDecimal("21.00"));
            BigDecimal precioAlAnadir = linea.getPrecioUnitario();

            // El catalogo sube de precio DESPUES de presupuestar.
            pieza.actualizarPrecios(new BigDecimal("120.0000"), new BigDecimal("199.0000"));

            assertThat(linea.getPrecioUnitario())
                    .as("la linea no debe seguir al catalogo")
                    .isEqualByComparingTo(precioAlAnadir)
                    .isEqualByComparingTo("20.0000");
        }

        @Test
        @DisplayName("numera las lineas correlativamente")
        void numeracionDeLineas() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            orden.iniciarDiagnostico(null, null);

            orden.anadirManoDeObra("Trabajo 1", BigDecimal.ONE, null, "GENERAL", new BigDecimal("21.00"));
            orden.anadirManoDeObra("Trabajo 2", BigDecimal.ONE, null, "GENERAL", new BigDecimal("21.00"));

            assertThat(orden.getLineas()).extracting(LineaOT::getNumeroLinea).containsExactly(1, 2);
        }

        @Test
        @DisplayName("no se anaden piezas dadas de baja")
        void piezaDeBaja() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            orden.iniciarDiagnostico(null, null);
            Pieza pieza = PiezasDePrueba.conStock(1L, "OBSOLETA", "0");
            pieza.darDeBaja();

            assertThatThrownBy(() -> orden.anadirPieza(pieza, BigDecimal.ONE, null,
                    new BigDecimal("21.00")))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("dada de baja");
        }

        @Test
        @DisplayName("no se editan lineas con la orden lista para entregar")
        void lineasBloqueadasEnLista() {
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon();
            orden.entrarEnReparacion(null, null);
            orden.marcarLista(null);

            assertThatThrownBy(() -> orden.anadirManoDeObra("Extra", BigDecimal.ONE, null,
                    "GENERAL", new BigDecimal("21.00")))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("LISTA");
        }

        @Test
        @DisplayName("la vista de lineas es de solo lectura")
        void lineasInmodificablesDesdeFuera() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();

            assertThatThrownBy(() -> orden.getLineas().add(null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Ciclo completo")
    class CicloCompleto {

        @Test
        @DisplayName("de recibida a entregada, dejando rastro de cada paso")
        void cicloCompleto() {
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon();
            orden.entrarEnReparacion(null, null);
            orden.marcarLista(null);
            orden.entregar(null);

            assertThat(orden.getEstado()).isEqualTo(EstadoOT.ENTREGADA);
            assertThat(orden.getFechaRealSalida()).isNotNull();
            assertThat(orden.getHistorialEstados())
                    .extracting(CambioEstadoOT::getEstadoNuevo)
                    .containsExactly(
                            EstadoOT.RECIBIDA, EstadoOT.EN_DIAGNOSTICO, EstadoOT.PRESUPUESTADA,
                            EstadoOT.APROBADA, EstadoOT.EN_REPARACION, EstadoOT.LISTA, EstadoOT.ENTREGADA);
        }

        @Test
        @DisplayName("una orden ENTREGADA no admite ningun cambio mas")
        void entregadaEsInmutable() {
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon();
            orden.entrarEnReparacion(null, null);
            orden.marcarLista(null);
            orden.entregar(null);

            assertThatThrownBy(() -> orden.marcarLista(null))
                    .isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(() -> orden.registrarDiagnostico("Otro diagnostico"))
                    .isInstanceOf(ConflictoException.class);
            assertThatThrownBy(() -> orden.anadirManoDeObra("Extra", BigDecimal.ONE, null,
                    "GENERAL", new BigDecimal("21.00")))
                    .isInstanceOf(ConflictoException.class);
        }

        @Test
        @DisplayName("bloqueo y reanudacion por falta de piezas")
        void bloqueoYReanudacion() {
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon();
            orden.entrarEnReparacion(null, null);

            orden.bloquearPorFaltaDePiezas("Sin existencias de ESP-RET-DER", null);
            assertThat(orden.getEstado()).isEqualTo(EstadoOT.ESPERANDO_PIEZAS);

            orden.entrarEnReparacion(null, "Recibido el pedido del proveedor");
            assertThat(orden.getEstado()).isEqualTo(EstadoOT.EN_REPARACION);

            assertThat(orden.getHistorialEstados())
                    .extracting(CambioEstadoOT::getEstadoNuevo)
                    .contains(EstadoOT.ESPERANDO_PIEZAS, EstadoOT.EN_REPARACION);
        }

        @Test
        @DisplayName("no se puede saltar del diagnostico directamente a la reparacion")
        void noSePuedeSaltarElPresupuesto() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            orden.iniciarDiagnostico(null, null);

            assertThatThrownBy(() -> orden.entrarEnReparacion(null, null))
                    .isInstanceOf(TransicionInvalidaException.class)
                    .hasMessageContaining("PRESUPUESTADA");
        }
    }

    @Nested
    @DisplayName("Totales")
    class Totales {

        @Test
        @DisplayName("suma las bases de todas las lineas")
        void sumaDeLineas() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            orden.iniciarDiagnostico(null, null);
            orden.anadirManoDeObra("Revision", new BigDecimal("2.5"), BigDecimal.ZERO, "GENERAL",
                    new BigDecimal("21.00"));

            // Los importes por linea los calcula PostgreSQL, asi que en un test
            // unitario sin base de datos vienen vacios: lo que se comprueba aqui
            // es que la suma no revienta con nulos.
            assertThat(orden.baseImponible()).isEqualByComparingTo("0");
            assertThat(orden.horasManoDeObra()).isEqualByComparingTo("2.5");
        }
    }
}
