package com.motorsport19.taller.cliente.service;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.cliente.domain.TipoDocumento;
import com.motorsport19.taller.cliente.repository.ClienteRepository;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de clientes")
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    /** Con trabajo abierto no se da de baja: el servicio lo consulta antes. */
    @Mock
    private com.motorsport19.taller.orden.repository.OrdenTrabajoRepository ordenRepository;

    @InjectMocks
    private ClienteService clienteService;

    private void guardarDevuelveElArgumento() {
        when(clienteRepository.save(any(Cliente.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("permite dar de alta con solo el nombre y el telefono")
        void altaSinDatosFiscales() {
            guardarDevuelveElArgumento();

            Cliente cliente = clienteService.crear("Rocio", "Almansa Gil", "600100107",
                    "rocio@correo.example", null, null, null, null, null, null, null);

            assertThat(cliente.nombreCompleto()).isEqualTo("Rocio Almansa Gil");
            // La ficha existe pero todavia no se le puede facturar.
            assertThat(cliente.tieneDatosFiscalesCompletos()).isFalse();
        }

        @Test
        @DisplayName("normaliza el documento a mayusculas y sin separadores")
        void normalizaElDocumento() {
            guardarDevuelveElArgumento();
            when(clienteRepository.existeConDocumento("12345678Z")).thenReturn(false);

            Cliente cliente = clienteService.crear("Carlos", "Nunez Prieto", "600100101", null,
                    TipoDocumento.NIF, " 12345678-z ", "Calle de Alcala 145", "28009", "Madrid",
                    "Madrid", "Espana");

            assertThat(cliente.getDocumento()).isEqualTo("12345678Z");
            assertThat(cliente.tieneDatosFiscalesCompletos()).isTrue();
        }

        @Test
        @DisplayName("rechaza un documento con digito de control incorrecto")
        void documentoConControlIncorrecto() {
            when(clienteRepository.existeConDocumento("12345678A")).thenReturn(false);

            assertThatThrownBy(() -> clienteService.crear("Carlos", "Nunez", "600100101", null,
                    TipoDocumento.NIF, "12345678A", "Calle X", "28009", "Madrid", "Madrid", "Espana"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("digito de control");

            verify(clienteRepository, never()).save(any());
        }

        @Test
        @DisplayName("rechaza un documento ya registrado")
        void documentoDuplicado() {
            when(clienteRepository.existeConDocumento("12345678Z")).thenReturn(true);
            when(clienteRepository.buscarPorDocumento("12345678Z"))
                    .thenReturn(Optional.of(Cliente.registrar("Carlos", "Nunez Prieto", null, null)));

            assertThatThrownBy(() -> clienteService.crear("Otro", "Cliente", null, null,
                    TipoDocumento.NIF, "12345678Z", "Calle X", "28009", "Madrid", "Madrid", "Espana"))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("Carlos Nunez Prieto");
        }

        @Test
        @DisplayName("exige el nombre")
        void nombreObligatorio() {
            assertThatThrownBy(() -> clienteService.crear("   ", null, null, null,
                    null, null, null, null, null, null, null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("nombre");
        }
    }

    @Nested
    @DisplayName("Datos fiscales")
    class DatosFiscales {

        @Test
        @DisplayName("una direccion incompleta deja al cliente sin poder facturar")
        void direccionIncompleta() {
            Cliente cliente = Cliente.registrar("Marta", "Iglesias Rubio", null, null);
            cliente.asignarDatosFiscales(TipoDocumento.NIF, "45678912S", "Avenida de America 22",
                    null, "Madrid", "Madrid", "Espana");

            assertThat(cliente.tieneDatosFiscalesCompletos()).isFalse();
        }

        @Test
        @DisplayName("deduce el tipo de documento cuando no se indica")
        void deduceElTipo() {
            Cliente cliente = Cliente.registrar("Talleres Delta S.L.", null, null, null);
            cliente.asignarDatosFiscales(null, "B86543212", "Poligono Las Mercedes 7",
                    "28022", "Madrid", "Madrid", "Espana");

            assertThat(cliente.getTipoDocumento()).isEqualTo(TipoDocumento.CIF);
            assertThat(cliente.tieneDatosFiscalesCompletos()).isTrue();
        }
    }

    @Nested
    @DisplayName("Baja logica")
    class Baja {

        @Test
        @DisplayName("marca la fecha de baja y desactiva")
        void darDeBaja() {
            Cliente cliente = Cliente.registrar("Ernesto", "Vidal Cano", null, null);
            when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

            clienteService.darDeBaja(1L);

            assertThat(cliente.isActivo()).isFalse();
            assertThat(cliente.getFechaBaja()).isNotNull();
        }

        @Test
        @DisplayName("no se puede dar de baja dos veces")
        void bajaRepetida() {
            Cliente cliente = Cliente.registrar("Ernesto", "Vidal Cano", null, null);
            cliente.darDeBaja();
            when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

            assertThatThrownBy(() -> clienteService.darDeBaja(1L))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("ya estaba dado de baja");
        }

        @Test
        @DisplayName("no se puede modificar un cliente dado de baja")
        void modificarClienteDeBaja() {
            Cliente cliente = Cliente.registrar("Ernesto", "Vidal Cano", null, null);
            cliente.darDeBaja();
            when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

            assertThatThrownBy(() -> clienteService.actualizarContacto(1L, "Ernesto", "Vidal", null, null, null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("reactivelo");
        }

        @Test
        @DisplayName("reactivar limpia la fecha de baja")
        void reactivar() {
            Cliente cliente = Cliente.registrar("Ernesto", "Vidal Cano", null, null);
            cliente.darDeBaja();
            when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

            clienteService.reactivar(1L);

            assertThat(cliente.isActivo()).isTrue();
            assertThat(cliente.getFechaBaja()).isNull();
        }
    }

    @Test
    @DisplayName("informa cuando el cliente no existe")
    void clienteInexistente() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.obtener(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");
    }
}
