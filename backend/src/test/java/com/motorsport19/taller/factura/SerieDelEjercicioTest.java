package com.motorsport19.taller.factura;

import com.motorsport19.taller.factura.domain.SerieFactura;
import com.motorsport19.taller.factura.domain.TipoFactura;
import com.motorsport19.taller.factura.repository.SerieFacturaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las series de facturacion que un taller necesita para empezar a trabajar.
 *
 * <p>Antes no venia ninguna: se instalaba el programa, se hacia la primera
 * reparacion y el boton de facturar fallaba, con el cliente esperando. Y la de
 * rectificativas se echaba de menos aun mas tarde: el dia que hay que corregir
 * una factura ya cobrada.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Series de facturacion del ejercicio")
class SerieDelEjercicioTest {

    @Mock
    private SerieFacturaRepository series;

    private SerieDelEjercicio arranque;
    private int esteAno;

    @BeforeEach
    void preparar() {
        arranque = new SerieDelEjercicio(series);
        esteAno = LocalDate.now().getYear();
    }

    private List<SerieFactura> guardadas() {
        ArgumentCaptor<SerieFactura> captor = ArgumentCaptor.forClass(SerieFactura.class);
        verify(series, times(2)).save(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    @DisplayName("Instalacion nueva")
    class InstalacionNueva {

        @Test
        @DisplayName("crea la serie ordinaria y la de rectificativas")
        void creaLasDos() {
            when(series.findAll()).thenReturn(List.of());

            arranque.run(null);

            List<SerieFactura> creadas = guardadas();
            assertThat(creadas).extracting(SerieFactura::getTipo)
                    .containsExactly(TipoFactura.ORDINARIA, TipoFactura.RECTIFICATIVA);
            assertThat(creadas).allMatch(s -> s.getEjercicio() == esteAno);
        }

        @Test
        @DisplayName("la ordinaria es la A y la de rectificar la RA")
        void codigosPorDefecto() {
            when(series.findAll()).thenReturn(List.of());

            arranque.run(null);

            assertThat(guardadas()).extracting(SerieFactura::getCodigo)
                    .containsExactly("A", "RA");
        }
    }

    @Nested
    @DisplayName("Instalacion en marcha")
    class YaEnMarcha {

        @Test
        @DisplayName("no toca nada si el ejercicio ya tiene sus dos series")
        void noPisaLoQueHay() {
            when(series.findAll()).thenReturn(List.of(
                    SerieFactura.crear("F", esteAno, "La del taller", TipoFactura.ORDINARIA),
                    SerieFactura.crear("RF", esteAno, "Rectificativas", TipoFactura.RECTIFICATIVA)));

            arranque.run(null);

            verify(series, never()).save(any());
        }

        @Test
        @DisplayName("al cambiar de ano hereda el codigo que ya usaba el taller")
        void heredaElCodigo() {
            // Un taller que factura con la serie «F» no quiere que el 1 de enero
            // le aparezca una «A»: la gestoria lleva anos viendo F/ano/numero.
            when(series.findAll()).thenReturn(List.of(
                    SerieFactura.crear("F", esteAno - 1, "La del taller", TipoFactura.ORDINARIA),
                    SerieFactura.crear("RF", esteAno - 1, "Rectificativas", TipoFactura.RECTIFICATIVA)));

            arranque.run(null);

            List<SerieFactura> creadas = guardadas();
            assertThat(creadas).extracting(SerieFactura::getCodigo).containsExactly("F", "RF");
            assertThat(creadas).allMatch(s -> s.getEjercicio() == esteAno);
        }

        @Test
        @DisplayName("si solo falta la de rectificativas, crea solo esa")
        void completaLaQueFalta() {
            when(series.findAll()).thenReturn(List.of(
                    SerieFactura.crear("A", esteAno, "Ordinaria", TipoFactura.ORDINARIA)));

            arranque.run(null);

            ArgumentCaptor<SerieFactura> captor = ArgumentCaptor.forClass(SerieFactura.class);
            verify(series, times(1)).save(captor.capture());
            assertThat(captor.getValue().getTipo()).isEqualTo(TipoFactura.RECTIFICATIVA);
            assertThat(captor.getValue().getCodigo()).isEqualTo("RA");
        }
    }
}
