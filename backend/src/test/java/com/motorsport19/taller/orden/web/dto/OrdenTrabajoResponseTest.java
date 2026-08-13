package com.motorsport19.taller.orden.web.dto;

import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.support.OrdenesDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La ficha que se le sirve a un tecnico.
 *
 * <p>Un taller puede decidir que quien monta la moto no vea a cuanto se la cobra
 * la casa al cliente. Esa decision no puede quedarse en no pintar una columna:
 * la API devuelve JSON y cualquiera lo lee desde el navegador. Estos tests
 * comprueban lo unico que de verdad la sostiene, que es que los importes no
 * salgan del servidor.
 */
@DisplayName("Orden de trabajo servida sin importes")
class OrdenTrabajoResponseTest {

    /** Campos que SI puede ver un tecnico aunque sean numeros: son trabajo, no dinero. */
    private static final List<String> NUMEROS_QUE_NO_SON_DINERO = List.of("cantidad", "horasManoDeObra");

    private static OrdenTrabajoResponse fichaCompleta() {
        OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon();
        return OrdenTrabajoResponse.de(orden, orden.getLineas(), orden.getHistorialEstados());
    }

    @Nested
    @DisplayName("Cabecera")
    class Cabecera {

        /**
         * Se comprueba con el bruto y la tarifa, que los calcula Java. La base
         * imponible, el IVA y el total los calcula la base de datos al insertar
         * la linea, y en un test unitario no hay ninguna: llegarian a nulo por
         * un motivo que no tiene nada que ver con lo que aqui se prueba.
         */
        @Test
        @DisplayName("la ficha normal lleva los importes")
        void laNormalSiLosLleva() {
            OrdenTrabajoResponse ficha = fichaCompleta();

            assertThat(ficha.importeBruto()).isNotNull().isGreaterThan(BigDecimal.ZERO);
            assertThat(ficha.tarifaHora()).isEqualTo(OrdenesDePrueba.TARIFA_HORA);
            assertThat(ficha.lineas().get(0).precioUnitario()).isNotNull();
        }

        @Test
        @DisplayName("sin importes no queda ni un euro en la cabecera")
        void ningunImporteSobrevive() {
            OrdenTrabajoResponse ficha = fichaCompleta().sinImportes();

            assertThat(ficha.tarifaHora()).isNull();
            assertThat(ficha.importeBruto()).isNull();
            assertThat(ficha.totalDescuento()).isNull();
            assertThat(ficha.baseImponible()).isNull();
            assertThat(ficha.totalIva()).isNull();
            assertThat(ficha.total()).isNull();
        }

        @Test
        @DisplayName("conserva lo que el tecnico necesita para trabajar")
        void conservaElTrabajo() {
            OrdenTrabajoResponse ficha = fichaCompleta().sinImportes();

            assertThat(ficha.codigo()).isNotBlank();
            assertThat(ficha.matricula()).isNotBlank();
            assertThat(ficha.problemaReportado()).isNotBlank();
            assertThat(ficha.diagnostico()).isNotBlank();
            assertThat(ficha.estadosPosibles()).isNotEmpty();
            // Las horas apuntadas son trabajo, no dinero: tiene que verlas.
            assertThat(ficha.horasManoDeObra()).isGreaterThan(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Lineas")
    class Lineas {

        @Test
        @DisplayName("sin importes se ve que hay que hacer y cuanto, pero no a cuanto")
        void lineaLimpia() {
            LineaOTResponse linea = fichaCompleta().sinImportes().lineas().get(0);

            assertThat(linea.descripcion()).isNotBlank();
            assertThat(linea.cantidad()).isGreaterThan(BigDecimal.ZERO);

            assertThat(linea.precioUnitario()).isNull();
            assertThat(linea.descuentoPct()).isNull();
            assertThat(linea.porcentajeIva()).isNull();
            assertThat(linea.importeBruto()).isNull();
            assertThat(linea.importeDescuento()).isNull();
            assertThat(linea.baseImponible()).isNull();
            assertThat(linea.cuotaIva()).isNull();
            assertThat(linea.total()).isNull();
        }

        /**
         * Este es el test que salta si manana alguien anade un campo de dinero a
         * la linea y se olvida de vaciarlo en {@code sinImportes()}. Enumerar los
         * campos a mano no protege de lo que todavia no existe.
         */
        @Test
        @DisplayName("ningun importe nuevo se cuela por olvido")
        void ningunCampoNuevoSeEscapa() throws Exception {
            LineaOTResponse linea = fichaCompleta().sinImportes().lineas().get(0);

            for (RecordComponent componente : LineaOTResponse.class.getRecordComponents()) {
                if (componente.getType() != BigDecimal.class
                        || NUMEROS_QUE_NO_SON_DINERO.contains(componente.getName())) {
                    continue;
                }
                assertThat(componente.getAccessor().invoke(linea))
                        .as("el campo %s viaja al tecnico con un importe dentro", componente.getName())
                        .isNull();
            }
        }
    }
}
