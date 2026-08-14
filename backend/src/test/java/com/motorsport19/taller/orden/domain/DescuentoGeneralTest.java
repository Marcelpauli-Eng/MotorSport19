package com.motorsport19.taller.orden.domain;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.support.OrdenesDePrueba;
import com.motorsport19.taller.support.PiezasDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El «Dto. General» del pie del presupuesto.
 *
 * <p>Escribe el mismo porcentaje en todas las lineas. Hacia arriba es justo lo
 * que se espera; hacia abajo se comia descuentos ya pactados con el cliente sin
 * decir nada, y el total <b>subia</b> despues de hacerle mas descuento.
 */
@DisplayName("Descuento general de la orden")
class DescuentoGeneralTest {

    /** Una orden en diagnostico, que es cuando se componen las lineas. */
    private OrdenTrabajo ordenConMaterial() {
        Pieza pieza = PiezasDePrueba.conStock(10L, "PZ-1", "50");
        OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
        orden.iniciarDiagnostico(null, null);
        orden.registrarDiagnostico("Hay que cambiar el kit");
        orden.anadirManoDeObra("Mano de obra", new BigDecimal("2.000"),
                BigDecimal.ZERO, "GENERAL", OrdenesDePrueba.IVA_GENERAL);
        orden.anadirPieza(pieza, BigDecimal.ONE, BigDecimal.ZERO, OrdenesDePrueba.IVA_GENERAL);
        return orden;
    }

    private LineaOT lineaDePieza(OrdenTrabajo orden) {
        return orden.lineasDePiezas().get(0);
    }

    /**
     * Lo que pagaria el cliente por las lineas, calculado aqui.
     *
     * <p>No se usa {@code baseImponible()} porque esa columna la calcula
     * PostgreSQL: en una prueba sin base de datos vale cero y cualquier
     * comparacion de importes saldria bien sin comprobar nada.
     */
    private BigDecimal neto(OrdenTrabajo orden) {
        return orden.getLineas().stream()
                .map(l -> l.importeBruto().multiply(
                        BigDecimal.ONE.subtract(l.getDescuentoPct()
                                .divide(new BigDecimal("100")))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("aplicado sobre lineas sin descuento, entra sin mas")
    void casoNormal() {
        OrdenTrabajo orden = ordenConMaterial();

        orden.aplicarDescuentoGeneral(new BigDecimal("10"));

        assertThat(orden.getLineas())
                .allMatch(l -> l.getDescuentoPct().compareTo(new BigDecimal("10")) == 0);
    }

    @Test
    @DisplayName("subirlo por encima de lo pactado entra: el cliente gana descuento")
    void haciaArriba() {
        OrdenTrabajo orden = ordenConMaterial();
        orden.cambiarDescuentoDeLinea(lineaDePieza(orden), new BigDecimal("10"));

        orden.aplicarDescuentoGeneral(new BigDecimal("25"));

        assertThat(orden.getLineas())
                .allMatch(l -> l.getDescuentoPct().compareTo(new BigDecimal("25")) == 0);
    }

    @Test
    @DisplayName("bajarlo por debajo de lo pactado se para y dice que linea es")
    void haciaAbajoSePara() {
        OrdenTrabajo orden = ordenConMaterial();
        orden.cambiarDescuentoDeLinea(lineaDePieza(orden), new BigDecimal("40"));
        BigDecimal antes = neto(orden);

        assertThatThrownBy(() -> orden.aplicarDescuentoGeneral(new BigDecimal("5")))
                .isInstanceOf(ReglaNegocioException.class)
                // Que linea pierde descuento y cuanto tenia.
                .hasMessageContaining("40")
                // Y por que se corta, en terminos de lo que le pasa al cliente.
                .hasMessageContaining("pagando mas");

        assertThat(neto(orden))
                .as("si se corta, no puede haber cambiado nada")
                .isEqualByComparingTo(antes);
    }

    @Test
    @DisplayName("confirmandolo si baja: corregir un 40 % puesto por error es legitimo")
    void haciaAbajoConfirmado() {
        OrdenTrabajo orden = ordenConMaterial();
        orden.cambiarDescuentoDeLinea(lineaDePieza(orden), new BigDecimal("40"));

        orden.aplicarDescuentoGeneral(new BigDecimal("5"), true);

        assertThat(orden.getLineas())
                .allMatch(l -> l.getDescuentoPct().compareTo(new BigDecimal("5")) == 0);
    }

    @Test
    @DisplayName("hacer mas descuento nunca deja al cliente pagando mas")
    void nuncaSubeElTotal() {
        OrdenTrabajo orden = ordenConMaterial();
        orden.cambiarDescuentoDeLinea(lineaDePieza(orden), new BigDecimal("25"));
        BigDecimal antes = neto(orden);

        // El «y ademas te hago un 5 %» del mostrador, que es lo que disparaba el
        // fallo: el 5 % se escribia encima del 25 % y la base subia.
        assertThatThrownBy(() -> orden.aplicarDescuentoGeneral(new BigDecimal("5")))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(neto(orden)).isLessThanOrEqualTo(antes);

        // Y por el camino bueno —un porcentaje mayor— la base solo puede bajar.
        orden.aplicarDescuentoGeneral(new BigDecimal("30"));
        assertThat(neto(orden)).isLessThan(antes);
    }
}
