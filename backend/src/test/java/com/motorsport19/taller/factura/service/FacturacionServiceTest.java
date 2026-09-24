package com.motorsport19.taller.factura.service;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.configuracion.repository.ConfiguracionTallerRepository;
import com.motorsport19.taller.factura.domain.TipoRectificativa;
import com.motorsport19.taller.factura.repository.ContadorRegistroFacturacionRepository;
import com.motorsport19.taller.factura.repository.FacturaRepository;
import com.motorsport19.taller.factura.repository.SerieFacturaRepository;
import com.motorsport19.taller.orden.service.OrdenTrabajoService;
import com.motorsport19.taller.support.FacturasDePrueba;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Emision de rectificativas")
class FacturacionServiceTest {

    @Mock private FacturaRepository facturaRepository;
    @Mock private SerieFacturaRepository serieRepository;
    @Mock private ContadorRegistroFacturacionRepository contadorRepository;
    @Mock private ConfiguracionTallerRepository configuracionRepository;
    @Mock private OrdenTrabajoService ordenService;
    @Mock private RegistroEventosService registroEventos;
    @Mock private EntityManager entityManager;

    @InjectMocks private FacturacionService servicio;

    @Test
    @DisplayName("no emite una por sustitucion: los informes la sumarian junto a la original")
    void sinSustitucion() {
        assertThatThrownBy(() -> servicio.emitirRectificativa(1L, 2L, TipoRectificativa.POR_SUSTITUCION,
                "Precio mal", List.of(FacturasDePrueba.manoDeObra("1", "40.0000")), null, 1L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("por diferencias");

        verifyNoInteractions(facturaRepository, contadorRepository, serieRepository);
    }
}
