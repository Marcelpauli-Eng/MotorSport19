package com.motorsport19.taller.configuracion.domain;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.configuracion.domain.ReglaCobro.Tipo;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.support.PiezasDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Que tasa o que plus le toca a una pieza.
 *
 * <p>Las dos acaban en la factura del cliente, asi que las dos equivocaciones
 * cuestan: no ponerla donde toca —la linea que se olvida— y ponerla donde no,
 * que es cobrar de mas o regalar un descuento.
 */
@DisplayName("Tasas y pluses")
class ReglaCobroTest {

    private static Pieza pieza(long id, String familia) {
        Pieza p = PiezasDePrueba.conStock(id, "P-" + id, "10");
        ReflectionTestUtils.setField(p, "familia", familia);
        return p;
    }

    private static ReglaCobro tasaDeGrupo(String familia, String euros) {
        return ReglaCobro.crear(Tipo.TASA, familia, null, "Tasa " + familia, new BigDecimal(euros), null);
    }

    @Test
    @DisplayName("la tasa de un grupo alcanza a todas sus piezas, sin mirar mayusculas ni espacios")
    void grupoEntero() {
        List<ReglaCobro> reglas = List.of(tasaDeGrupo("Neumaticos", "1.50"));

        assertThat(ReglaCobro.laQueManda(reglas, Tipo.TASA, pieza(1, "Neumaticos"))).isPresent();
        assertThat(ReglaCobro.laQueManda(reglas, Tipo.TASA, pieza(2, " NEUMATICOS "))).isPresent();
        assertThat(ReglaCobro.laQueManda(reglas, Tipo.TASA, pieza(3, "Filtros"))).isEmpty();
        assertThat(ReglaCobro.laQueManda(reglas, Tipo.TASA, pieza(4, null))).isEmpty();
        assertThat(ReglaCobro.laQueManda(reglas, Tipo.PLUS, pieza(1, "Neumaticos"))).isEmpty();
    }

    @Test
    @DisplayName("la regla de una pieza manda sobre la de su grupo, y solo para ella")
    void piezaConcreta() {
        Pieza grande = pieza(9, "Neumaticos");
        ReglaCobro suya = ReglaCobro.crear(Tipo.TASA, "Neumaticos", grande, "Tasa grande",
                new BigDecimal("2.50"), null);
        List<ReglaCobro> reglas = List.of(tasaDeGrupo("Neumaticos", "1.50"), suya);

        assertThat(ReglaCobro.laQueManda(reglas, Tipo.TASA, grande)).contains(suya);
        assertThat(ReglaCobro.laQueManda(reglas, Tipo.TASA, pieza(8, "Neumaticos")).orElseThrow()
                .getValor()).isEqualByComparingTo("1.50");
        // Con pieza, el grupo no se guarda: si la pieza cambia de grupo, la regla la sigue.
        assertThat(suya.getFamilia()).isNull();
    }

    @Test
    @DisplayName("una tasa necesita concepto e importe; un plus, un descuento de hasta el 100 %")
    void validaciones() {
        assertThatThrownBy(() -> ReglaCobro.crear(Tipo.TASA, "Neumaticos", null, " ", BigDecimal.ONE, null))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("concepto");
        assertThatThrownBy(() -> tasaDeGrupo("Neumaticos", "0"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> ReglaCobro.crear(Tipo.PLUS, "Filtros", null, null, new BigDecimal("101"), null))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("100");
        assertThatThrownBy(() -> ReglaCobro.crear(Tipo.PLUS, " ", null, null, BigDecimal.TEN, null))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("grupo o la pieza");
    }

    @Test
    @DisplayName("sin unidad, la de siempre; en % no pasa del 100 y en euros si puede")
    void unidades() {
        assertThat(tasaDeGrupo("Neumaticos", "1").getUnidad()).isEqualTo(ReglaCobro.Unidad.EUROS);
        assertThat(ReglaCobro.crear(Tipo.PLUS, "Filtros", null, null, BigDecimal.TEN, null).getUnidad())
                .isEqualTo(ReglaCobro.Unidad.PORCENTAJE);
        assertThatThrownBy(() -> ReglaCobro.crear(Tipo.TASA, "Neumaticos", null, "Tasa", new BigDecimal("101"),
                ReglaCobro.Unidad.PORCENTAJE)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("100");
        assertThat(ReglaCobro.crear(Tipo.PLUS, "Motores", null, null, new BigDecimal("150"),
                ReglaCobro.Unidad.EUROS).getValor()).isEqualByComparingTo("150");
    }

    @Test
    @DisplayName("una tasa en % es ese tanto del precio neto de la pieza; en euros, su importe")
    void importeDeTasa() {
        assertThat(ReglaCobro.crear(Tipo.TASA, "Neumaticos", null, "Tasa", new BigDecimal("2.5"),
                ReglaCobro.Unidad.PORCENTAJE).importeDeTasa(new BigDecimal("127.03"))).isEqualByComparingTo("3.18");
        assertThat(tasaDeGrupo("Neumaticos", "1.50").importeDeTasa(new BigDecimal("127.03")))
                .isEqualByComparingTo("1.50");
    }

    @Test
    @DisplayName("dos reglas del mismo tipo para lo mismo se reconocen como repetidas")
    void repetidas() {
        assertThat(tasaDeGrupo("Neumaticos", "1").mismaQue(tasaDeGrupo("neumaticos", "2"))).isTrue();
        assertThat(tasaDeGrupo("Neumaticos", "1").mismaQue(
                ReglaCobro.crear(Tipo.PLUS, "Neumaticos", null, null, BigDecimal.TEN, null))).isFalse();
    }
}
