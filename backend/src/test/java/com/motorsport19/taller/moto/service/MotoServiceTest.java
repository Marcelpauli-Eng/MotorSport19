package com.motorsport19.taller.moto.service;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.cliente.service.ClienteService;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.moto.repository.MotoRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de motos")
class MotoServiceTest {

    @Mock
    private MotoRepository motoRepository;

    @Mock
    private ClienteService clienteService;

    @InjectMocks
    private MotoService motoService;

    private Cliente cliente;

    @BeforeEach
    void prepararCliente() {
        cliente = Cliente.registrar("Carlos", "Nunez Prieto", "600100101", null);
    }

    private Moto motoDePrueba(int km) {
        return Moto.registrar(cliente, "1234 JKL", "Yamaha", "MT-07", 2021, 689, "Azul",
                "JYARM33E0MA012345", km, null);
    }

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("normaliza la matricula a la forma canonica")
        void normalizaLaMatricula() {
            when(clienteService.obtener(1L)).thenReturn(cliente);
            when(motoRepository.existeConMatricula("1234 JKL")).thenReturn(false);
            when(motoRepository.save(any(Moto.class))).thenAnswer(i -> i.getArgument(0));

            // En mostrador se teclea de cualquier forma; en la base de datos
            // solo debe quedar una.
            Moto moto = motoService.crear(1L, "1234-jkl", "Yamaha", "MT-07", 2021, 689,
                    "Azul", null, 24500, null);

            assertThat(moto.getMatricula()).isEqualTo("1234 JKL");
        }

        @Test
        @DisplayName("rechaza una matricula ya registrada")
        void matriculaDuplicada() {
            when(clienteService.obtener(1L)).thenReturn(cliente);
            when(motoRepository.existeConMatricula("1234 JKL")).thenReturn(true);

            assertThatThrownBy(() -> motoService.crear(1L, "1234 JKL", "Yamaha", "MT-07",
                    2021, 689, null, null, 0, null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("1234 JKL");

            verify(motoRepository, never()).save(any());
        }

        @Test
        @DisplayName("no permite dar de alta motos a un cliente de baja")
        void clienteDeBaja() {
            cliente.darDeBaja();
            when(clienteService.obtener(1L)).thenReturn(cliente);

            assertThatThrownBy(() -> motoService.crear(1L, "1234 JKL", "Yamaha", "MT-07",
                    2021, 689, null, null, 0, null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("dado de baja");
        }

        @Test
        @DisplayName("rechaza un ano imposible")
        void anioImposible() {
            when(clienteService.obtener(1L)).thenReturn(cliente);
            when(motoRepository.existeConMatricula(anyString())).thenReturn(false);

            assertThatThrownBy(() -> motoService.crear(1L, "1234 JKL", "Yamaha", "MT-07",
                    1800, 689, null, null, 0, null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("1800");
        }

        @Test
        @DisplayName("rechaza un bastidor ya registrado")
        void bastidorDuplicado() {
            when(clienteService.obtener(1L)).thenReturn(cliente);
            when(motoRepository.existeConMatricula(anyString())).thenReturn(false);
            when(motoRepository.existeOtraConBastidor(anyString(), anyLong())).thenReturn(true);

            assertThatThrownBy(() -> motoService.crear(1L, "1234 JKL", "Yamaha", "MT-07",
                    2021, 689, null, "JYARM33E0MA012345", 0, null))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("bastidor");
        }
    }

    @Nested
    @DisplayName("Kilometraje")
    class Kilometraje {

        @Test
        @DisplayName("acepta una lectura mayor que la registrada")
        void kilometrajeCreciente() {
            Moto moto = motoDePrueba(24500);
            when(motoRepository.buscarConCliente(1L)).thenReturn(Optional.of(moto));

            motoService.registrarKilometraje(1L, 26100);

            assertThat(moto.getKmActual()).isEqualTo(26100);
        }

        @Test
        @DisplayName("acepta la misma lectura")
        void mismoKilometraje() {
            Moto moto = motoDePrueba(24500);
            when(motoRepository.buscarConCliente(1L)).thenReturn(Optional.of(moto));

            motoService.registrarKilometraje(1L, 24500);

            assertThat(moto.getKmActual()).isEqualTo(24500);
        }

        @Test
        @DisplayName("rechaza una lectura menor: el cuentakilometros no retrocede")
        void kilometrajeDecreciente() {
            Moto moto = motoDePrueba(24500);
            when(motoRepository.buscarConCliente(1L)).thenReturn(Optional.of(moto));

            // O es un error de tecleo o alguien ha tocado el cuadro; en ambos
            // casos hay que mirarlo, no guardarlo en silencio.
            assertThatThrownBy(() -> motoService.registrarKilometraje(1L, 20000))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no puede disminuir");

            assertThat(moto.getKmActual()).isEqualTo(24500);
        }
    }

    @Nested
    @DisplayName("Cambio de propietario")
    class CambioPropietario {

        @Test
        @DisplayName("asigna la moto al nuevo cliente")
        void cambiaElPropietario() {
            Moto moto = motoDePrueba(24500);
            Cliente nuevo = Cliente.registrar("Marta", "Iglesias Rubio", null, null);
            when(motoRepository.buscarConCliente(1L)).thenReturn(Optional.of(moto));
            when(clienteService.obtener(2L)).thenReturn(nuevo);

            motoService.cambiarPropietario(1L, 2L);

            assertThat(moto.getCliente()).isSameAs(nuevo);
        }

        @Test
        @DisplayName("no permite asignarla a un cliente de baja")
        void nuevoPropietarioDeBaja() {
            Moto moto = motoDePrueba(24500);
            Cliente nuevo = Cliente.registrar("Marta", "Iglesias Rubio", null, null);
            nuevo.darDeBaja();
            when(motoRepository.buscarConCliente(1L)).thenReturn(Optional.of(moto));
            when(clienteService.obtener(2L)).thenReturn(nuevo);

            assertThatThrownBy(() -> motoService.cambiarPropietario(1L, 2L))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("dado de baja");
        }
    }
}
