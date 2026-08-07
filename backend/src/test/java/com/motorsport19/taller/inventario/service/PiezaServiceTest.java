package com.motorsport19.taller.inventario.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.configuracion.repository.TipoIvaRepository;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.inventario.repository.PiezaRepository;
import com.motorsport19.taller.support.PiezasDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de piezas")
class PiezaServiceTest {

    @Mock
    private PiezaRepository piezaRepository;

    @Mock
    private ProveedorService proveedorService;

    @Mock
    private TipoIvaRepository tipoIvaRepository;

    @Mock
    private InventarioService inventarioService;

    @InjectMocks
    private PiezaService piezaService;

    @Test
    @DisplayName("una pieza nueva nace con stock cero")
    void naceConStockCero() {
        when(piezaRepository.existeConSku("ACE-10W40-1L")).thenReturn(false);
        when(tipoIvaRepository.existsById("GENERAL")).thenReturn(true);
        when(piezaRepository.saveAndFlush(any(Pieza.class))).thenAnswer(i -> i.getArgument(0));

        Pieza pieza = piezaService.crear("ACE-10W40-1L", "Aceite motor 10W-40", "Motul", "A1-01",
                "Aceites y liquidos", new BigDecimal("12"), new BigDecimal("6.20"), new BigDecimal("12.90"), "GENERAL",
                null, "L", null, null, null);

        // Las existencias no se escriben nunca en la pieza: es la base de datos
        // quien las deriva de los movimientos.
        assertThat(pieza.existencias()).isEqualByComparingTo("0");
        verify(inventarioService, never()).registrarEntrada(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("el stock inicial se carga como movimiento de entrada, no escribiendo la pieza")
    void stockInicialGeneraMovimiento() {
        when(piezaRepository.existeConSku("BUJ-CR8E")).thenReturn(false);
        when(tipoIvaRepository.existsById("GENERAL")).thenReturn(true);
        when(piezaRepository.saveAndFlush(any(Pieza.class)))
                .thenAnswer(i -> PiezasDePrueba.conStock(7L, "BUJ-CR8E", "0"));

        piezaService.crear("BUJ-CR8E", "Bujia NGK CR8E", "NGK", "A2-01", "Encendido",
                new BigDecimal("8"),
                new BigDecimal("3.90"), new BigDecimal("8.75"), "GENERAL", null, "UD", null,
                new BigDecimal("24"), 1L);

        // Asi hasta la primera unidad del almacen queda explicada en el libro.
        verify(inventarioService).registrarEntrada(7L, new BigDecimal("24"), null,
                new BigDecimal("3.90"), "Carga inicial de inventario", 1L);
    }

    @Test
    @DisplayName("rechaza un SKU ya usado")
    void skuDuplicado() {
        when(piezaRepository.existeConSku("ACE-10W40-1L")).thenReturn(true);

        assertThatThrownBy(() -> piezaService.crear("ACE-10W40-1L", "Aceite", null, null, null,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, "GENERAL", null, "L", null, null, null))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("ACE-10W40-1L");
    }

    @Test
    @DisplayName("rechaza un tipo de IVA que no existe en el catalogo")
    void tipoIvaInexistente() {
        when(piezaRepository.existeConSku(anyString())).thenReturn(false);
        when(tipoIvaRepository.existsById("INVENTADO")).thenReturn(false);

        assertThatThrownBy(() -> piezaService.crear("SKU-1", "Pieza", null, null, null,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, "INVENTADO", null, "UD", null, null, null))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("INVENTADO");
    }

    @Test
    @DisplayName("no deja dar de baja una pieza que todavia tiene existencias")
    void bajaConExistencias() {
        when(piezaRepository.buscarConProveedor(1L))
                .thenReturn(Optional.of(PiezasDePrueba.conStock(1L, "ACE-10W40-1L", "36")));

        // Darla de baja con stock dejaria 36 unidades en el almacen que ya nadie
        // podria usar ni facturar.
        assertThatThrownBy(() -> piezaService.darDeBaja(1L))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("36");
    }

    @Test
    @DisplayName("permite dar de baja una pieza sin existencias")
    void bajaSinExistencias() {
        Pieza pieza = PiezasDePrueba.conStock(1L, "ESP-RET-DER", "0");
        when(piezaRepository.buscarConProveedor(1L)).thenReturn(Optional.of(pieza));

        piezaService.darDeBaja(1L);

        assertThat(pieza.isActivo()).isFalse();
        assertThat(pieza.getFechaBaja()).isNotNull();
    }

    @Test
    @DisplayName("cambiar el precio de catalogo no toca las existencias")
    void cambiarPreciosNoAfectaAlStock() {
        Pieza pieza = PiezasDePrueba.conStock(1L, "ACE-10W40-1L", "36");
        when(piezaRepository.buscarConProveedor(1L)).thenReturn(Optional.of(pieza));

        piezaService.actualizarPrecios(1L, new BigDecimal("6.80"), new BigDecimal("13.90"));

        assertThat(pieza.getPrecioVenta()).isEqualByComparingTo("13.90");
        assertThat(pieza.existencias()).isEqualByComparingTo("36");
    }

    @Test
    @DisplayName("detecta correctamente si la pieza esta bajo minimos")
    void deteccionDeBajoMinimo() {
        assertThat(PiezasDePrueba.con(1L, "JUN-CUL", "2", "2").estaBajoMinimo()).isTrue();
        assertThat(PiezasDePrueba.con(2L, "CAB-EMB", "1", "3").estaBajoMinimo()).isTrue();
        assertThat(PiezasDePrueba.con(3L, "ACE-10W40-1L", "36", "12").estaBajoMinimo()).isFalse();
        assertThat(PiezasDePrueba.con(4L, "ESP-RET-DER", "0", "2").sinExistencias()).isTrue();
    }
}
