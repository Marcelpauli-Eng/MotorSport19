package com.motorsport19.taller.configuracion.web;

import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.configuracion.repository.ConfiguracionTallerRepository;
import com.motorsport19.taller.configuracion.repository.TipoIvaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests de la pantalla de datos del taller.
 *
 * <p>Lo que se protege aqui es que una instalacion recien hecha se pueda
 * terminar desde el propio programa. La fila de configuracion no la crea
 * ninguna migracion a proposito (unos datos fiscales de relleno acabarian
 * impresos en una factura de verdad), asi que si esta pantalla exigiera que la
 * fila ya existiese, el taller quedaria bloqueado: sin configuracion no se abren
 * ordenes, y sin abrir la pantalla no hay forma de ponerla.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Datos del taller")
class ConfiguracionControllerTest {

    @Mock private ConfiguracionTallerRepository repositorio;
    @Mock private TipoIvaRepository tiposIva;

    @InjectMocks private ConfiguracionController controlador;

    private static ConfiguracionController.ActualizarConfiguracion peticion() {
        return new ConfiguracionController.ActualizarConfiguracion(
                "Taller Ejemplo S.L.", "B12345674", "Calle Mayor 1", "28001", "Madrid",
                "Madrid", "ES", "910000000", "taller@ejemplo.example",
                new BigDecimal("45.00"), "GENERAL", new BigDecimal("16.00"));
    }

    @Nested
    @DisplayName("Taller sin estrenar")
    class SinConfigurar {

        @Test
        @DisplayName("la consulta responde en blanco en vez de fallar")
        void consultaEnBlanco() {
            when(repositorio.findById(ConfiguracionTaller.ID_UNICO)).thenReturn(Optional.empty());
            when(tiposIva.findAll()).thenReturn(List.of());

            ConfiguracionController.ConfiguracionResponse respuesta = controlador.obtener();

            assertThat(respuesta.configurado()).isFalse();
            assertThat(respuesta.razonSocial()).isNull();
        }

        @Test
        @DisplayName("guardar crea la fila con los datos que pone el taller")
        void guardarCrea() {
            when(repositorio.findById(ConfiguracionTaller.ID_UNICO)).thenReturn(Optional.empty());
            when(repositorio.save(any(ConfiguracionTaller.class)))
                    .thenAnswer(llamada -> llamada.getArgument(0));
            when(tiposIva.findAll()).thenReturn(List.of());

            ConfiguracionController.ConfiguracionResponse respuesta =
                    controlador.actualizar(peticion());

            ArgumentCaptor<ConfiguracionTaller> guardada =
                    ArgumentCaptor.forClass(ConfiguracionTaller.class);
            org.mockito.Mockito.verify(repositorio).save(guardada.capture());
            assertThat(guardada.getValue().getRazonSocial()).isEqualTo("Taller Ejemplo S.L.");
            assertThat(guardada.getValue().getTarifaHoraDefecto()).isEqualByComparingTo("45.00");
            // El programa emisor se identifica solo: no lo elige el taller.
            assertThat(guardada.getValue().getSoftwareNombre())
                    .isEqualTo(ConfiguracionTaller.SOFTWARE_NOMBRE);
            assertThat(respuesta.configurado()).isTrue();
        }
    }

    @Nested
    @DisplayName("Taller ya configurado")
    class YaConfigurado {

        @Test
        @DisplayName("guardar cambia la fila que ya habia, sin crear otra")
        void guardarActualiza() {
            ConfiguracionTaller existente = ConfiguracionTaller.sinRellenar();
            existente.actualizar("Antiguo S.L.", "B12345674", "Calle Vieja 2", "28002", "Madrid",
                    "Madrid", "ES", null, null, new BigDecimal("30.00"), "GENERAL",
                    new BigDecimal("8.00"));
            when(repositorio.findById(ConfiguracionTaller.ID_UNICO))
                    .thenReturn(Optional.of(existente));
            when(repositorio.save(any(ConfiguracionTaller.class)))
                    .thenAnswer(llamada -> llamada.getArgument(0));
            when(tiposIva.findAll()).thenReturn(List.of());

            ConfiguracionController.ConfiguracionResponse respuesta =
                    controlador.actualizar(peticion());

            assertThat(respuesta.razonSocial()).isEqualTo("Taller Ejemplo S.L.");
            assertThat(existente.getTarifaHoraDefecto()).isEqualByComparingTo("45.00");
        }
    }

    @Test
    @DisplayName("sin provincia la guarda vacia: la columna no admite nulos")
    void provinciaVacia() {
        ConfiguracionTaller cfg = ConfiguracionTaller.sinRellenar();

        cfg.actualizar("Taller Ejemplo S.L.", "B12345674", "Calle Mayor 1", "28001", "Madrid",
                null, "ES", null, null, new BigDecimal("45.00"), "GENERAL", new BigDecimal("16.00"));

        assertThat(cfg.getProvincia()).isEmpty();
    }
}
