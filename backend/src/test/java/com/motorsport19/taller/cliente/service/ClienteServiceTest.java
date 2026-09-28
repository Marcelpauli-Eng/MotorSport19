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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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

    @Mock
    private com.motorsport19.taller.fichaje.service.RegistroActividad registroActividad;

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
                    "rocio@correo.example", null, null, null, null, null, null, null, null);

            assertThat(cliente.nombreCompleto()).isEqualTo("Rocio Almansa Gil");
            // La ficha existe pero todavia no se le puede facturar.
            assertThat(cliente.tieneDatosFiscalesCompletos()).isFalse();
        }

        @Test
        @DisplayName("guarda las observaciones que se escriben en el alta")
        void altaConObservaciones() {
            guardarDevuelveElArgumento();

            Cliente cliente = clienteService.crear("Paula", null, "600555111", null,
                    null, null, null, null, null, null, null, "Llamar por la tarde");

            assertThat(cliente.getObservaciones()).isEqualTo("Llamar por la tarde");
        }

        @Test
        @DisplayName("normaliza el documento a mayusculas y sin separadores")
        void normalizaElDocumento() {
            guardarDevuelveElArgumento();
            when(clienteRepository.existeConDocumento("12345678Z")).thenReturn(false);

            Cliente cliente = clienteService.crear("Carlos", "Nunez Prieto", "600100101", null,
                    TipoDocumento.NIF, " 12345678-z ", "Calle de Alcala 145", "28009", "Madrid",
                    "Madrid", "Espana", null);

            assertThat(cliente.getDocumento()).isEqualTo("12345678Z");
            assertThat(cliente.tieneDatosFiscalesCompletos()).isTrue();
        }

        @Test
        @DisplayName("rechaza un documento con digito de control incorrecto")
        void documentoConControlIncorrecto() {
            when(clienteRepository.existeConDocumento("12345678A")).thenReturn(false);

            assertThatThrownBy(() -> clienteService.crear("Carlos", "Nunez", "600100101", null,
                    TipoDocumento.NIF, "12345678A", "Calle X", "28009", "Madrid", "Madrid", "Espana", null))
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
                    TipoDocumento.NIF, "12345678Z", "Calle X", "28009", "Madrid", "Madrid", "Espana", null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("Carlos Nunez Prieto");
        }

        @Test
        @DisplayName("exige el nombre")
        void nombreObligatorio() {
            assertThatThrownBy(() -> clienteService.crear("   ", null, null, null,
                    null, null, null, null, null, null, null, null))
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

        @Test
        @DisplayName("corregir la direccion sin mandar el pais conserva el que tenia")
        void conservaElPais() {
            Cliente cliente = Cliente.registrar("Joao", "Silva", null, null);
            cliente.asignarDatosFiscales(TipoDocumento.PASAPORTE, "P1234567", "Rua Augusta 10",
                    "1100-053", "Lisboa", "Lisboa", "Portugal");

            cliente.asignarDatosFiscales(TipoDocumento.PASAPORTE, "P1234567", "Rua Augusta 12",
                    "1100-053", "Lisboa", "Lisboa", null);

            assertThat(cliente.getPais()).isEqualTo("Portugal");
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

    @Nested
    @DisplayName("Importacion")
    class Importacion {

        @Test
        @DisplayName("volver a importar a quien no tiene documento no lo duplica")
        void noDuplicaSinDocumento() {
            // Llega con espacios, como sale de una hoja de calculo: se compara ya limpio.
            when(clienteRepository.existeIgual("Rocio", "Almansa Gil", "600100107", null)).thenReturn(true);

            assertThatThrownBy(() -> clienteService.importar(" Rocio ", "Almansa Gil ", "600100107", "",
                    null, null, null, null, null, null, null, null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("Rocio Almansa Gil");
            verify(clienteRepository, never()).save(any(Cliente.class));
        }

        @Test
        @DisplayName("con documento, quien decide si ya esta es el documento")
        void conDocumentoMandaElDocumento() {
            when(clienteRepository.existeConDocumento("12345678Z")).thenReturn(true);

            assertThatThrownBy(() -> clienteService.importar("Rocio", null, null, null,
                    null, "12345678Z", "Calle Mayor 1", "28001", "Madrid", "Madrid", null, null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("12345678Z");
            verify(clienteRepository, never()).existeIgual(any(), any(), any(), any());
        }

        @Test
        @DisplayName("un NIF que no cuadra no deja fuera al cliente: entra sin el y queda anotado")
        void documentoQueNoValeQuedaEnObservaciones() {
            guardarDevuelveElArgumento();

            Cliente cliente = clienteService.importar("Marta", "Ruiz Soler", "600100107", null,
                    null, "12345678a", "Calle X", "28009", "Madrid", "Madrid", null, "Viene del programa anterior");

            assertThat(cliente.getDocumento()).isNull();
            assertThat(cliente.getObservaciones()).isEqualTo(
                    "Viene del programa anterior\nDocumento del fichero importado, que no es valido: 12345678A");
            // Sin documento, reimportarlo no lo duplica: se busca por nombre y contacto.
            verify(clienteRepository).existeIgual("Marta", "Ruiz Soler", "600100107", null);
        }

        @Test
        @DisplayName("un pasaporte no se comprueba: entra como documento")
        void pasaporteSeAdmite() {
            guardarDevuelveElArgumento();

            Cliente cliente = clienteService.importar("Pierre", "Martin", null, null,
                    TipoDocumento.PASAPORTE, "12AB34567", "Rue X", "75001", "Paris", "Paris", "Francia", null);

            assertThat(cliente.getDocumento()).isEqualTo("12AB34567");
            assertThat(cliente.getTipoDocumento()).isEqualTo(TipoDocumento.PASAPORTE);
        }

        @Test
        @DisplayName("encuentra al propietario por su NIF, escrito como sea")
        void identificaPorDocumento() {
            Cliente rocio = Cliente.registrar("Rocio", "Almansa Gil", null, null);
            ReflectionTestUtils.setField(rocio, "id", 7L);
            when(clienteRepository.buscarPorDocumento("12345678Z")).thenReturn(Optional.of(rocio));

            assertThat(clienteService.identificar(" 12.345.678-z ")).isEqualTo(7L);
        }

        @Test
        @DisplayName("sin NIF lo encuentra por el nombre completo de su ficha")
        void identificaPorNombre() {
            when(clienteRepository.buscarPorDocumento(any())).thenReturn(Optional.empty());
            when(clienteRepository.idsConNombreCompleto("Rocio Almansa Gil")).thenReturn(List.of(7L));

            assertThat(clienteService.identificar("Rocio   Almansa Gil")).isEqualTo(7L);
        }

        @Test
        @DisplayName("con dos clientes que se llaman igual no elige uno a ciegas")
        void nombreRepetido() {
            when(clienteRepository.buscarPorDocumento(any())).thenReturn(Optional.empty());
            when(clienteRepository.idsConNombreCompleto("Juan Garcia")).thenReturn(List.of(3L, 9L));

            assertThatThrownBy(() -> clienteService.identificar("Juan Garcia"))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("NIF");
        }

        @Test
        @DisplayName("dice que cliente no existe y cuando falta la columna")
        void clienteQueNoEsta() {
            when(clienteRepository.buscarPorDocumento(any())).thenReturn(Optional.empty());
            when(clienteRepository.idsConNombreCompleto("Nadie")).thenReturn(List.of());

            assertThatThrownBy(() -> clienteService.identificar("Nadie"))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("'Nadie'");
            assertThatThrownBy(() -> clienteService.identificar("  "))
                    .isInstanceOf(ReglaNegocioException.class);
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
