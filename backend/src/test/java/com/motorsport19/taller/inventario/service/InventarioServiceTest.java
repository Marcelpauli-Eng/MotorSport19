package com.motorsport19.taller.inventario.service;

import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.MovimientoStock;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.inventario.domain.TipoMovimiento;
import com.motorsport19.taller.inventario.repository.MovimientoStockRepository;
import com.motorsport19.taller.inventario.repository.PiezaRepository;
import com.motorsport19.taller.support.PiezasDePrueba;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del servicio de inventario.
 *
 * <p>El foco esta en las reglas que protegen el almacen: que el signo del
 * movimiento sea siempre coherente con su tipo, que no se pueda sacar mas de lo
 * que hay, y que las correcciones se hagan anadiendo asientos en vez de
 * reescribiendo el libro.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de inventario")
class InventarioServiceTest {

    @Mock
    private PiezaRepository piezaRepository;

    @Mock
    private MovimientoStockRepository movimientoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EntityManager entityManager;

    private InventarioService inventarioService;

    @BeforeEach
    void prepararServicio() {
        inventarioService = new InventarioService(piezaRepository, movimientoRepository, usuarioRepository,
                entityManager);
    }

    /** Devuelve el movimiento que el servicio ha intentado persistir. */
    private MovimientoStock movimientoGuardado() {
        ArgumentCaptor<MovimientoStock> captor = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private void dadaLaPieza(Pieza pieza) {
        when(piezaRepository.buscarParaActualizarStock(pieza.getId())).thenReturn(Optional.of(pieza));
        when(movimientoRepository.saveAndFlush(any(MovimientoStock.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    // ==================================================================

    @Nested
    @DisplayName("Entradas")
    class Entradas {

        @Test
        @DisplayName("registra la entrada con cantidad positiva")
        void entradaSumaExistencias() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "ACE-10W40-1L", "12");
            dadaLaPieza(pieza);

            inventarioService.registrarEntrada(1L, new BigDecimal("40"), "ALB-2026-0412",
                    new BigDecimal("6.20"), "Compra de temporada", null);

            MovimientoStock movimiento = movimientoGuardado();
            assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
            assertThat(movimiento.getCantidad()).isEqualByComparingTo("40");
            assertThat(movimiento.getDocumentoProveedor()).isEqualTo("ALB-2026-0412");
            assertThat(movimiento.getPrecioCosteUnitario()).isEqualByComparingTo("6.20");
        }

        @Test
        @DisplayName("rechaza una entrada de cantidad cero o negativa")
        void entradaExigeCantidadPositiva() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "ACE-10W40-1L", "12");
            when(piezaRepository.buscarParaActualizarStock(1L)).thenReturn(Optional.of(pieza));

            assertThatThrownBy(() -> inventarioService.registrarEntrada(1L, BigDecimal.ZERO, null, null, null, null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("mayor que cero");

            verify(movimientoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("falla si la pieza no existe")
        void entradaSobrePiezaInexistente() {
            when(piezaRepository.buscarParaActualizarStock(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventarioService.registrarEntrada(99L, BigDecimal.TEN, null, null, null, null))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("Salidas")
    class Salidas {

        @Test
        @DisplayName("guarda la cantidad en negativo aunque se pida en positivo")
        void salidaGuardaCantidadNegativa() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "BUJ-CR8E", "24");
            dadaLaPieza(pieza);

            inventarioService.registrarSalida(1L, new BigDecimal("2"), "Uso interno del taller", null);

            MovimientoStock movimiento = movimientoGuardado();
            assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.SALIDA);
            // El signo lo pone el dominio: quien llama pide 2 unidades, no -2.
            assertThat(movimiento.getCantidad()).isEqualByComparingTo("-2");
        }

        @Test
        @DisplayName("rechaza sacar mas unidades de las que hay")
        void salidaSinExistenciasSuficientes() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "ESP-RET-DER", "1");
            when(piezaRepository.buscarParaActualizarStock(1L)).thenReturn(Optional.of(pieza));

            assertThatThrownBy(() -> inventarioService.registrarSalida(1L, new BigDecimal("3"), "Reparacion", null))
                    .isInstanceOf(StockInsuficienteException.class)
                    .hasMessageContaining("ESP-RET-DER");

            verify(movimientoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("la excepcion de stock detalla cuantas unidades faltan")
        void excepcionDetallaElDescuadre() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "ESP-RET-DER", "1");
            when(piezaRepository.buscarParaActualizarStock(1L)).thenReturn(Optional.of(pieza));

            // La fase 3 usara estos datos para mover la OT a ESPERANDO_PIEZAS
            // indicando cuantas unidades hay que pedir al proveedor.
            assertThatThrownBy(() -> inventarioService.registrarSalida(1L, new BigDecimal("4"), "Reparacion", null))
                    .isInstanceOfSatisfying(StockInsuficienteException.class, ex -> {
                        assertThat(ex.getSku()).isEqualTo("ESP-RET-DER");
                        assertThat(ex.getDisponible()).isEqualByComparingTo("1");
                        assertThat(ex.getSolicitado()).isEqualByComparingTo("4");
                        assertThat(ex.faltan()).isEqualByComparingTo("3");
                    });
        }

        @Test
        @DisplayName("permite consumir exactamente todas las existencias")
        void salidaDeTodoElStock() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "KIT-TRA-525", "4");
            dadaLaPieza(pieza);

            inventarioService.registrarSalida(1L, new BigDecimal("4"), "Ultimo kit disponible", null);

            assertThat(movimientoGuardado().getCantidad()).isEqualByComparingTo("-4");
        }

        @Test
        @DisplayName("exige motivo cuando no hay orden de trabajo asociada")
        void salidaSinOrdenExigeMotivo() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "LAM-H4", "20");
            when(piezaRepository.buscarParaActualizarStock(1L)).thenReturn(Optional.of(pieza));

            assertThatThrownBy(() -> inventarioService.registrarSalida(1L, BigDecimal.ONE, "   ", null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("motivo");
        }
    }

    @Nested
    @DisplayName("Ajustes de inventario")
    class Ajustes {

        @Test
        @DisplayName("acepta un ajuste negativo cuando hay existencias suficientes")
        void ajusteNegativo() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "ESP-RET-DER", "2");
            dadaLaPieza(pieza);

            inventarioService.registrarAjuste(1L, new BigDecimal("-2"),
                    "Inventario fisico: 2 unidades danadas", null);

            MovimientoStock movimiento = movimientoGuardado();
            assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.AJUSTE);
            assertThat(movimiento.getCantidad()).isEqualByComparingTo("-2");
        }

        @Test
        @DisplayName("acepta un ajuste positivo cuando aparecen unidades")
        void ajustePositivo() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "FIL-ACE-HF204", "16");
            dadaLaPieza(pieza);

            inventarioService.registrarAjuste(1L, new BigDecimal("3"),
                    "Inventario fisico: 3 unidades no registradas", null);

            assertThat(movimientoGuardado().getCantidad()).isEqualByComparingTo("3");
        }

        @Test
        @DisplayName("exige motivo siempre")
        void ajusteExigeMotivo() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "FIL-ACE-HF204", "16");
            when(piezaRepository.buscarParaActualizarStock(1L)).thenReturn(Optional.of(pieza));

            assertThatThrownBy(() -> inventarioService.registrarAjuste(1L, new BigDecimal("3"), null, null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("motivo");

            verify(movimientoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("rechaza un ajuste de cantidad cero")
        void ajusteCeroNoAportaNada() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "FIL-ACE-HF204", "16");
            when(piezaRepository.buscarParaActualizarStock(1L)).thenReturn(Optional.of(pieza));

            assertThatThrownBy(() -> inventarioService.registrarAjuste(1L, BigDecimal.ZERO, "Recuento", null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("distinta de cero");
        }

        @Test
        @DisplayName("un ajuste negativo no puede dejar el stock en negativo")
        void ajusteNegativoExcesivo() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "JUN-CUL", "2");
            when(piezaRepository.buscarParaActualizarStock(1L)).thenReturn(Optional.of(pieza));

            assertThatThrownBy(() -> inventarioService.registrarAjuste(1L, new BigDecimal("-5"), "Recuento", null))
                    .isInstanceOf(StockInsuficienteException.class);
        }
    }

    @Nested
    @DisplayName("Devoluciones")
    class Devoluciones {

        @Test
        @DisplayName("devuelve al almacen con cantidad positiva")
        void devolucionSuma() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "PAS-FRE-DEL-SBS", "9");
            dadaLaPieza(pieza);

            inventarioService.registrarDevolucion(1L, new BigDecimal("1"), null, null,
                    "Pieza no utilizada en la reparacion", null);

            MovimientoStock movimiento = movimientoGuardado();
            assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.DEVOLUCION);
            assertThat(movimiento.getCantidad()).isEqualByComparingTo("1");
        }
    }

    @Nested
    @DisplayName("Consulta de existencias")
    class Existencias {

        @Test
        @DisplayName("confirma que hay existencias suficientes")
        void hayExistencias() {
            when(piezaRepository.findById(1L))
                    .thenReturn(Optional.of(PiezasDePrueba.conStock(1L, "ACE-10W40-1L", "36")));

            assertThat(inventarioService.hayExistenciasSuficientes(1L, new BigDecimal("3"))).isTrue();
        }

        @Test
        @DisplayName("detecta que no hay existencias suficientes")
        void noHayExistencias() {
            when(piezaRepository.findById(1L))
                    .thenReturn(Optional.of(PiezasDePrueba.conStock(1L, "ESP-RET-DER", "0")));

            assertThat(inventarioService.hayExistenciasSuficientes(1L, BigDecimal.ONE)).isFalse();
        }
    }

    @Nested
    @DisplayName("Relectura del stock")
    class Relectura {

        @Test
        @DisplayName("relee la pieza tras el movimiento para devolver el saldo actualizado")
        void refrescaLaPiezaTrasElMovimiento() {
            Pieza pieza = PiezasDePrueba.conStock(1L, "ACE-10W40-1L", "36");
            dadaLaPieza(pieza);

            inventarioService.registrarEntrada(1L, new BigDecimal("10"), null, null, "Reposicion", null);

            // El stock lo escribe un trigger, no Hibernate: sin releer la entidad
            // devolveriamos el saldo anterior al movimiento.
            verify(entityManager).refresh(pieza);
        }
    }
}
