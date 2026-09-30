package com.motorsport19.taller.solicitud.service;

import com.motorsport19.taller.agenda.service.CitaService;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.solicitud.domain.FotoSolicitud;
import com.motorsport19.taller.solicitud.domain.SolicitudWeb;
import com.motorsport19.taller.solicitud.domain.TipoSolicitud;
import com.motorsport19.taller.solicitud.repository.FotoSolicitudRepository;
import com.motorsport19.taller.solicitud.repository.SolicitudWebRepository;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Bandeja de solicitudes de la web")
class SolicitudWebServiceTest {

    private static final String JPEG = Base64.getEncoder().encodeToString(
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10});

    @Mock
    private SolicitudWebRepository solicitudes;
    @Mock
    private FotoSolicitudRepository fotos;
    @Mock
    private CitaService citaService;
    @Mock
    private UsuarioRepository usuarios;

    @InjectMocks
    private SolicitudWebService servicio;

    private static SolicitudWebService.Entrada entrada(List<String> fotos) {
        return new SolicitudWebService.Entrada("ref-1", TipoSolicitud.PRESUPUESTO, "es", "Ana Soler",
                "600100200", "ana@example.com", "Honda", "CBR 600", null, "Carenado tras una caida",
                null, fotos);
    }

    @Test
    @DisplayName("guarda la solicitud y sus fotos en orden")
    void recibeConFotos() {
        when(solicitudes.findByReferencia("ref-1")).thenReturn(Optional.empty());
        when(solicitudes.save(any())).thenAnswer(i -> i.getArgument(0));

        SolicitudWeb s = servicio.recibir(entrada(List.of(JPEG, JPEG)));

        assertThat(s.getNumFotos()).isEqualTo((short) 2);
        var guardadas = ArgumentCaptor.forClass(FotoSolicitud.class);
        verify(fotos, times(2)).save(guardadas.capture());
        assertThat(guardadas.getAllValues()).extracting(FotoSolicitud::getOrden).containsExactly((short) 1, (short) 2);
    }

    @Test
    @DisplayName("un reenvio con la misma referencia no duplica nada")
    void reenvio() {
        SolicitudWeb existente = SolicitudWeb.recibir("ref-1", TipoSolicitud.CITA, "es", "Ana", "600100200",
                null, "Honda", "CBR", null, "Frenos", null, 0);
        when(solicitudes.findByReferencia("ref-1")).thenReturn(Optional.of(existente));

        assertThat(servicio.recibir(entrada(List.of(JPEG)))).isSameAs(existente);
        verify(solicitudes, never()).save(any());
        verifyNoInteractions(fotos);
    }

    @Test
    @DisplayName("fotos mal codificadas o de mas: se rechaza entera, sin guardar nada")
    void fotosMalas() {
        when(solicitudes.findByReferencia("ref-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.recibir(entrada(List.of("esto no es base64!"))))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.recibir(entrada(List.of(JPEG, JPEG, JPEG, JPEG))))
                .isInstanceOf(ReglaNegocioException.class);
        verify(solicitudes, never()).save(any());
    }

    @Test
    @DisplayName("si ya estaba atendida no se crea una cita suelta en la agenda")
    void darCitaAUnaAtendida() {
        SolicitudWeb atendida = SolicitudWeb.recibir("ref-1", TipoSolicitud.CITA, "es", "Ana", "600100200",
                null, "Honda", "CBR", null, "Frenos", null, 0);
        atendida.marcarAtendida(null, null);
        when(solicitudes.findConRelacionesById(7L)).thenReturn(Optional.of(atendida));

        assertThatThrownBy(() -> servicio.darCita(7L, Instant.now(), BigDecimal.ONE, null, null, "Ana",
                "600100200", "Honda CBR", "Frenos", null, null, 1L))
                .isInstanceOf(ConflictoException.class);
        verifyNoInteractions(citaService);
    }
}
