package com.motorsport19.taller.orden.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.configuracion.repository.ConfiguracionTallerRepository;
import com.motorsport19.taller.configuracion.domain.TipoIva;
import com.motorsport19.taller.configuracion.repository.TipoIvaRepository;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.inventario.repository.MovimientoStockRepository;
import com.motorsport19.taller.inventario.service.InventarioService;
import com.motorsport19.taller.inventario.service.PiezaService;
import com.motorsport19.taller.inventario.service.IntentoConsumo;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.moto.service.MotoService;
import com.motorsport19.taller.orden.domain.ContadorOt;
import com.motorsport19.taller.orden.domain.EstadoOT;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.orden.repository.CambioEstadoOTRepository;
import com.motorsport19.taller.orden.repository.ContadorOtRepository;
import com.motorsport19.taller.orden.repository.LineaOTRepository;
import com.motorsport19.taller.orden.repository.OrdenTrabajoRepository;
import com.motorsport19.taller.seguridad.UsuarioActual;
import com.motorsport19.taller.support.OrdenesDePrueba;
import com.motorsport19.taller.support.PiezasDePrueba;
import com.motorsport19.taller.usuario.domain.Rol;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del servicio de ordenes de trabajo.
 *
 * <p>El foco esta en el cruce entre la maquina de estados y el inventario: que
 * entrar en reparacion consuma el material, que la falta de existencias mande la
 * orden a ESPERANDO_PIEZAS en vez de dejar el stock en negativo, y que al
 * reanudar no se vuelva a consumir lo que ya salio del almacen.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Servicio de ordenes de trabajo")
class OrdenTrabajoServiceTest {

    @Mock private OrdenTrabajoRepository ordenRepository;
    @Mock private LineaOTRepository lineaRepository;
    @Mock private ContadorOtRepository contadorRepository;
    @Mock private CambioEstadoOTRepository cambioEstadoRepository;
    @Mock private MovimientoStockRepository movimientoRepository;
    @Mock private MotoService motoService;
    @Mock private PiezaService piezaService;
    @Mock private InventarioService inventarioService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TipoIvaRepository tipoIvaRepository;
    @Mock private ConfiguracionTallerRepository configuracionRepository;
    /** Por defecto no es tecnico, asi que no se aplica el filtro por asignacion. */
    @Mock private UsuarioActual usuarioActual;

    @InjectMocks
    private OrdenTrabajoService ordenService;

    /** Por defecto, ninguna linea ha consumido nada todavia. */
    private void sinConsumoPrevio() {
        when(movimientoRepository.consumoNetoDeLinea(anyLong())).thenReturn(BigDecimal.ZERO);
        when(inventarioService.intentarConsumoEnOrden(anyLong(), any(), any(), any(), any()))
                .thenReturn(servido());
    }

    /** Intento que no pudo servirse: cero disponibles, una unidad pedida. */
    private static IntentoConsumo sinExistencias() {
        return new IntentoConsumo(false, BigDecimal.ZERO, BigDecimal.ONE, null);
    }

    /** Intento servido correctamente. */
    private static IntentoConsumo servido() {
        return new IntentoConsumo(true, BigDecimal.TEN, BigDecimal.ONE, null);
    }

    private void dadaLaOrden(OrdenTrabajo orden) {
        when(ordenRepository.buscarConDetalle(orden.getId())).thenReturn(Optional.of(orden));
    }

    // ==================================================================

    @Nested
    @DisplayName("Apertura")
    class Apertura {

        private void configuracionConTarifa(String tarifa) {
            // La entidad tiene el constructor protegido a proposito (solo JPA la
            // instancia); para el test basta con lo unico que se le pide.
            ConfiguracionTaller config = org.mockito.Mockito.mock(ConfiguracionTaller.class);
            when(config.getTarifaHoraDefecto()).thenReturn(new BigDecimal(tarifa));
            when(configuracionRepository.findById(ConfiguracionTaller.ID_UNICO))
                    .thenReturn(Optional.of(config));
        }

        @Test
        @DisplayName("consume el siguiente numero del contador del ejercicio")
        void asignaNumeroCorrelativo() {
            Moto moto = OrdenesDePrueba.moto(OrdenesDePrueba.cliente());
            ReflectionTestUtils.setField(moto, "id", 1L);
            ContadorOt contador = ContadorOt.para(2026);
            ReflectionTestUtils.setField(contador, "ultimoNumero", 41);

            when(motoService.obtener(1L)).thenReturn(moto);
            when(ordenRepository.contarAbiertasDeMoto(1L)).thenReturn(0L);
            when(contadorRepository.bloquearEjercicio(any())).thenReturn(Optional.of(contador));
            configuracionConTarifa("45.00");
            when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(i -> i.getArgument(0));

            OrdenTrabajo orden = ordenService.abrir(1L, "Ruido en la transmision", 26000,
                    null, null, null, null);

            assertThat(orden.getNumero()).isEqualTo(42);
            assertThat(orden.getEstado()).isEqualTo(EstadoOT.RECIBIDA);
            assertThat(orden.getTarifaHora()).isEqualByComparingTo("45.00");
        }

        @Test
        @DisplayName("aprovecha el kilometraje de entrada para actualizar el de la moto")
        void actualizaElKilometraje() {
            Moto moto = OrdenesDePrueba.moto(OrdenesDePrueba.cliente());
            ReflectionTestUtils.setField(moto, "id", 1L);
            when(motoService.obtener(1L)).thenReturn(moto);
            when(ordenRepository.contarAbiertasDeMoto(1L)).thenReturn(0L);
            when(contadorRepository.bloquearEjercicio(any()))
                    .thenReturn(Optional.of(ContadorOt.para(2026)));
            configuracionConTarifa("45.00");
            when(ordenRepository.save(any(OrdenTrabajo.class))).thenAnswer(i -> i.getArgument(0));

            ordenService.abrir(1L, "Revision", 26000, null, null, null, null);

            assertThat(moto.getKmActual()).isEqualTo(26000);
        }

        @Test
        @DisplayName("no abre una segunda orden para una moto que ya tiene una sin cerrar")
        void motoConOrdenAbierta() {
            Moto moto = OrdenesDePrueba.moto(OrdenesDePrueba.cliente());
            ReflectionTestUtils.setField(moto, "id", 1L);
            when(motoService.obtener(1L)).thenReturn(moto);
            when(ordenRepository.contarAbiertasDeMoto(1L)).thenReturn(1L);

            assertThatThrownBy(() -> ordenService.abrir(1L, "Otra averia", 26000, null, null, null, null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("sin cerrar");

            verify(ordenRepository, never()).save(any());
        }

        @Test
        @DisplayName("falla si el taller no tiene configurada la tarifa por hora")
        void sinConfiguracion() {
            Moto moto = OrdenesDePrueba.moto(OrdenesDePrueba.cliente());
            ReflectionTestUtils.setField(moto, "id", 1L);
            when(motoService.obtener(1L)).thenReturn(moto);
            when(ordenRepository.contarAbiertasDeMoto(1L)).thenReturn(0L);
            when(contadorRepository.bloquearEjercicio(any()))
                    .thenReturn(Optional.of(ContadorOt.para(2026)));
            when(configuracionRepository.findById(ConfiguracionTaller.ID_UNICO))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> ordenService.abrir(1L, "Revision", 100, null, null, null, null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("tarifa por hora");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Entrada en reparacion con consumo de almacen")
    class ConsumoDeMaterial {

        @Test
        @DisplayName("con material suficiente, consume todo y entra en reparacion")
        void consumoCompleto() {
            Pieza aceite = PiezasDePrueba.conStock(10L, "ACE-10W40-1L", "36");
            Pieza filtro = PiezasDePrueba.conStock(11L, "FIL-ACE-HF204", "16");
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon(aceite, filtro);
            dadaLaOrden(orden);
            sinConsumoPrevio();

            ResultadoConsumo resultado = ordenService.iniciarReparacion(1L, null);

            assertThat(resultado.completo()).isTrue();
            assertThat(resultado.estadoResultante()).isEqualTo(EstadoOT.EN_REPARACION);
            assertThat(resultado.consumidas()).isEqualTo(2);
            assertThat(orden.getEstado()).isEqualTo(EstadoOT.EN_REPARACION);

            verify(inventarioService).intentarConsumoEnOrden(eq(10L), any(), eq(orden), any(), any());
            verify(inventarioService).intentarConsumoEnOrden(eq(11L), any(), eq(orden), any(), any());
        }

        @Test
        @DisplayName("sin existencias, la orden va a ESPERANDO_PIEZAS en vez de dejar stock negativo")
        void faltaMaterial() {
            Pieza espejo = PiezasDePrueba.conStock(16L, "ESP-RET-DER", "0");
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon(espejo);
            dadaLaOrden(orden);
            sinConsumoPrevio();

            when(inventarioService.intentarConsumoEnOrden(eq(16L), any(), any(), any(), any()))
                    .thenReturn(sinExistencias());

            ResultadoConsumo resultado = ordenService.iniciarReparacion(1L, null);

            assertThat(resultado.completo()).isFalse();
            assertThat(resultado.estadoResultante()).isEqualTo(EstadoOT.ESPERANDO_PIEZAS);
            assertThat(orden.getEstado()).isEqualTo(EstadoOT.ESPERANDO_PIEZAS);
            assertThat(resultado.faltantes()).singleElement().satisfies(f -> {
                assertThat(f.sku()).isEqualTo("ESP-RET-DER");
                assertThat(f.faltan()).isEqualByComparingTo("1");
            });
        }

        @Test
        @DisplayName("consume lo que hay y bloquea solo por lo que falta")
        void consumoParcial() {
            Pieza aceite = PiezasDePrueba.conStock(10L, "ACE-10W40-1L", "36");
            Pieza espejo = PiezasDePrueba.conStock(16L, "ESP-RET-DER", "0");
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon(aceite, espejo);
            dadaLaOrden(orden);
            sinConsumoPrevio();

            when(inventarioService.intentarConsumoEnOrden(eq(16L), any(), any(), any(), any()))
                    .thenReturn(sinExistencias());

            ResultadoConsumo resultado = ordenService.iniciarReparacion(1L, null);

            // El mecanico empieza por lo que puede montar; esas piezas ya estan
            // apartadas para esta moto.
            verify(inventarioService).intentarConsumoEnOrden(eq(10L), any(), any(), any(), any());
            assertThat(resultado.consumidas()).isEqualTo(1);
            assertThat(resultado.estadoResultante()).isEqualTo(EstadoOT.ESPERANDO_PIEZAS);
            assertThat(resultado.faltantes()).hasSize(1);
        }

        @Test
        @DisplayName("el motivo del bloqueo detalla que hay que pedir al proveedor")
        void motivoDelBloqueo() {
            Pieza espejo = PiezasDePrueba.conStock(16L, "ESP-RET-DER", "0");
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon(espejo);
            dadaLaOrden(orden);
            sinConsumoPrevio();
            when(inventarioService.intentarConsumoEnOrden(anyLong(), any(), any(), any(), any()))
                    .thenReturn(sinExistencias());

            ResultadoConsumo resultado = ordenService.iniciarReparacion(1L, null);

            assertThat(resultado.descripcionDeFaltantes()).contains("ESP-RET-DER", "faltan 1");
            assertThat(orden.getHistorialEstados())
                    .last()
                    .satisfies(c -> assertThat(c.getMotivo()).contains("ESP-RET-DER"));
        }

        @Test
        @DisplayName("al reanudar no vuelve a consumir lo que ya salio del almacen")
        void reanudarNoDuplicaConsumo() {
            Pieza aceite = PiezasDePrueba.conStock(10L, "ACE-10W40-1L", "36");
            Pieza espejo = PiezasDePrueba.conStock(16L, "ESP-RET-DER", "3");
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon(aceite, espejo);
            orden.entrarEnReparacion(null, null);
            orden.bloquearPorFaltaDePiezas("Sin existencias de ESP-RET-DER", null);
            dadaLaOrden(orden);

            LineaOT lineaAceite = orden.lineasDePiezas().get(0);
            LineaOT lineaEspejo = orden.lineasDePiezas().get(1);

            // El aceite ya se consumio en el primer intento; el espejo no.
            when(movimientoRepository.consumoNetoDeLinea(lineaAceite.getId())).thenReturn(BigDecimal.ONE);
            when(movimientoRepository.consumoNetoDeLinea(lineaEspejo.getId())).thenReturn(BigDecimal.ZERO);
            // Ahora el proveedor ya ha servido el espejo.
            when(inventarioService.intentarConsumoEnOrden(anyLong(), any(), any(), any(), any()))
                    .thenReturn(servido());

            ResultadoConsumo resultado = ordenService.reanudarReparacion(1L, null);

            verify(inventarioService, never())
                    .intentarConsumoEnOrden(eq(10L), any(), any(), any(), any());
            verify(inventarioService)
                    .intentarConsumoEnOrden(eq(16L), any(), any(), any(), any());
            assertThat(resultado.estadoResultante()).isEqualTo(EstadoOT.EN_REPARACION);
            assertThat(orden.getEstado()).isEqualTo(EstadoOT.EN_REPARACION);
        }

        @Test
        @DisplayName("si al reanudar sigue faltando, la orden se queda bloqueada")
        void reanudarSinExistencias() {
            Pieza espejo = PiezasDePrueba.conStock(16L, "ESP-RET-DER", "0");
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon(espejo);
            orden.entrarEnReparacion(null, null);
            orden.bloquearPorFaltaDePiezas("Sin existencias", null);
            dadaLaOrden(orden);
            sinConsumoPrevio();
            when(inventarioService.intentarConsumoEnOrden(anyLong(), any(), any(), any(), any()))
                    .thenReturn(sinExistencias());

            int historialAntes = orden.getHistorialEstados().size();
            ResultadoConsumo resultado = ordenService.reanudarReparacion(1L, null);

            assertThat(resultado.estadoResultante()).isEqualTo(EstadoOT.ESPERANDO_PIEZAS);
            assertThat(orden.getEstado()).isEqualTo(EstadoOT.ESPERANDO_PIEZAS);
            // No se anota una transicion a un estado en el que ya estaba.
            assertThat(orden.getHistorialEstados()).hasSize(historialAntes);
        }

        @Test
        @DisplayName("no se entra en reparacion desde un estado que no lo permite")
        void estadoIncorrecto() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            dadaLaOrden(orden);

            assertThatThrownBy(() -> ordenService.iniciarReparacion(1L, null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("RECIBIDA");

            verify(inventarioService, never())
                    .intentarConsumoEnOrden(anyLong(), any(), any(), any(), any());
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Gestion de lineas")
    class GestionDeLineas {

        @Test
        @DisplayName("no se quita una linea que ya ha consumido material")
        void noSeQuitaLineaConsumida() {
            Pieza aceite = PiezasDePrueba.conStock(10L, "ACE-10W40-1L", "36");
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon(aceite);
            dadaLaOrden(orden);

            LineaOT linea = orden.lineasDePiezas().get(0);
            when(movimientoRepository.consumoNetoDeLinea(linea.getId())).thenReturn(BigDecimal.ONE);

            // El libro de movimientos es inmutable: no se puede borrar el rastro
            // de unas piezas que salieron fisicamente del almacen.
            assertThatThrownBy(() -> ordenService.quitarLinea(1L, linea.getId()))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("Devuelvalas al almacen");
        }

        @Test
        @DisplayName("si no ha consumido nada, la linea se puede quitar")
        void seQuitaLineaNoConsumida() {
            Pieza aceite = PiezasDePrueba.conStock(10L, "ACE-10W40-1L", "36");
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon(aceite);
            dadaLaOrden(orden);
            sinConsumoPrevio();

            LineaOT linea = orden.lineasDePiezas().get(0);
            int lineasAntes = orden.getLineas().size();

            ordenService.quitarLinea(1L, linea.getId());

            assertThat(orden.getLineas()).hasSize(lineasAntes - 1);
        }

        @Test
        @DisplayName("no se baja la cantidad por debajo de lo ya consumido")
        void cantidadPorDebajoDelConsumo() {
            Pieza aceite = PiezasDePrueba.conStock(10L, "ACE-10W40-1L", "36");
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon(aceite);
            dadaLaOrden(orden);

            LineaOT linea = orden.lineasDePiezas().get(0);
            OrdenesDePrueba.fijarCantidad(linea, "3");
            when(movimientoRepository.consumoNetoDeLinea(linea.getId())).thenReturn(new BigDecimal("3"));

            assertThatThrownBy(() -> ordenService.cambiarCantidadDeLinea(1L, linea.getId(),
                    new BigDecimal("1")))
                    .isInstanceOf(com.motorsport19.taller.common.error.ReglaNegocioException.class)
                    .hasMessageContaining("Devuelvalas");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("Un tecnico solo trabaja sus ordenes")
    class PermisosDeTecnico {

        /** Tipo de IVA general, que es lo unico que necesita anadir una linea. */
        private TipoIva ivaGeneral() {
            TipoIva iva = org.springframework.beans.BeanUtils.instantiateClass(TipoIva.class);
            ReflectionTestUtils.setField(iva, "codigo", "GENERAL");
            ReflectionTestUtils.setField(iva, "porcentaje", OrdenesDePrueba.IVA_GENERAL);
            return iva;
        }

        /** Pone al usuario actual como TECNICO con el id indicado. */
        private void comoTecnico(long id) {
            when(usuarioActual.esTecnico()).thenReturn(true);
            when(usuarioActual.id()).thenReturn(id);
        }

        /** Deja la orden asignada a un tecnico concreto. */
        private void asignada(OrdenTrabajo orden, long tecnicoId) {
            Usuario tecnico = Usuario.crear("nsanz", "hash", "Nuria Sanz Belmonte",
                    null, null, Rol.TECNICO);
            ReflectionTestUtils.setField(tecnico, "id", tecnicoId);
            orden.asignarTecnico(tecnico);
        }

        @Test
        @DisplayName("no puede anadir mano de obra a la orden de otro")
        void manoDeObraEnOrdenAjena() {
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon();
            asignada(orden, 4L);
            dadaLaOrden(orden);
            comoTecnico(3L);

            assertThatThrownBy(() -> ordenService.anadirManoDeObra(1L, "Intruso",
                    BigDecimal.ONE, BigDecimal.ZERO, "GENERAL"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("otro tecnico");
        }

        @Test
        @DisplayName("no puede anadir piezas a la orden de otro, ni tocar el almacen")
        void piezaEnOrdenAjena() {
            Pieza aceite = PiezasDePrueba.conStock(10L, "ACE-10W40-1L", "36");
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon();
            asignada(orden, 4L);
            dadaLaOrden(orden);
            comoTecnico(3L);

            assertThatThrownBy(() -> ordenService.anadirPieza(1L, aceite.getId(),
                    BigDecimal.ONE, BigDecimal.ZERO))
                    .isInstanceOf(AccessDeniedException.class);
            verify(piezaService, never()).obtener(anyLong());
        }

        @Test
        @DisplayName("si puede trabajar la orden que tiene asignada")
        void ordenPropia() {
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon();
            asignada(orden, 3L);
            dadaLaOrden(orden);
            comoTecnico(3L);
            when(tipoIvaRepository.findById("GENERAL")).thenReturn(Optional.of(ivaGeneral()));

            assertThat(ordenService.anadirManoDeObra(1L, "Sustitucion de pastillas",
                    BigDecimal.ONE, BigDecimal.ZERO, "GENERAL")).isNotNull();
        }

        @Test
        @DisplayName("puede hacerse cargo de una orden que aun no es de nadie")
        void cogeOrdenSinAsignar() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            when(ordenRepository.buscarConDetalle(1L)).thenReturn(Optional.of(orden));
            comoTecnico(3L);

            ordenService.iniciarDiagnostico(1L, null, null);

            assertThat(orden.getEstado()).isEqualTo(EstadoOT.EN_DIAGNOSTICO);
        }

        @Test
        @DisplayName("no puede quitarle a un companero una orden ya asignada")
        void noRobaOrdenAsignada() {
            OrdenTrabajo orden = OrdenesDePrueba.recienAbierta();
            asignada(orden, 4L);
            when(ordenRepository.buscarConDetalle(1L)).thenReturn(Optional.of(orden));
            comoTecnico(3L);

            assertThatThrownBy(() -> ordenService.iniciarDiagnostico(1L, 3L, null))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("otro tecnico");
        }

        @Test
        @DisplayName("mostrador y administracion no tienen esta restriccion")
        void mostradorSinRestriccion() {
            OrdenTrabajo orden = OrdenesDePrueba.aprobadaCon();
            asignada(orden, 4L);
            dadaLaOrden(orden);
            when(usuarioActual.esTecnico()).thenReturn(false);
            when(tipoIvaRepository.findById("GENERAL")).thenReturn(Optional.of(ivaGeneral()));

            assertThat(ordenService.anadirManoDeObra(1L, "Ajuste de presupuesto",
                    BigDecimal.ONE, BigDecimal.ZERO, "GENERAL")).isNotNull();
        }
    }
}
