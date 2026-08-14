package com.motorsport19.taller.moto.service;

import com.motorsport19.taller.moto.repository.MotoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Buscar una moto tecleando la matricula como la teclea todo el mundo.
 *
 * <p>La matricula se guarda en su forma canonica, «1234 ABC» con el espacio. En
 * mostrador nadie la escribe asi: se copia del permiso de circulacion o del
 * llavero de un tiron, o con guion. Buscando solo por el texto literal, la moto
 * que si estaba fichada no aparecia; quien atendia daba por hecho que era nueva,
 * la creaba otra vez, y ahi se topaba con un «ya existe esa matricula» que no
 * explica nada.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Busqueda de motos por matricula")
class BusquedaDeMatriculaTest {

    @Mock
    private MotoRepository motoRepository;

    @Mock
    private com.motorsport19.taller.cliente.service.ClienteService clienteService;

    @InjectMocks
    private MotoService motoService;

    private final Page<com.motorsport19.taller.moto.domain.Moto> vacia =
            new PageImpl<>(List.of());

    @ParameterizedTest(name = "«{0}» busca tambien por «1234 ABC»")
    @ValueSource(strings = {"1234ABC", "1234-ABC", "1234abc", "1234.abc"})
    @DisplayName("la matricula se normaliza sea como sea que se teclee")
    void normalizaLaMatricula(String tecleado) {
        when(motoRepository.buscar(any(), any(), eq(true), any())).thenReturn(vacia);

        motoService.buscar(tecleado, true, PageRequest.of(0, 20));

        verify(motoRepository).buscar(any(), eq("1234 ABC"), eq(true), any());
    }

    @ParameterizedTest(name = "«{0}» no necesita la copia normalizada")
    @ValueSource(strings = {"1234 ABC", "1234 abc", " 1234 abc "})
    @DisplayName("si ya viene canonica no se manda dos veces lo mismo")
    void yaCanonica(String tecleado) {
        when(motoRepository.buscar(any(), any(), eq(true), any())).thenReturn(vacia);

        motoService.buscar(tecleado, true, PageRequest.of(0, 20));

        // El texto literal ya cubre la busqueda —la consulta compara en
        // mayusculas—, asi que la copia solo anadiria una condicion identica.
        verify(motoRepository).buscar(eq(tecleado.trim()), isNull(), eq(true), any());
    }

    @Test
    @DisplayName("buscar por marca o modelo no se convierte en matricula")
    void textoQueNoEsMatricula() {
        when(motoRepository.buscar(any(), any(), eq(true), any())).thenReturn(vacia);

        motoService.buscar("Yamaha", true, PageRequest.of(0, 20));

        verify(motoRepository).buscar(eq("Yamaha"), isNull(), eq(true), any());
    }

    @Test
    @DisplayName("el listado sin filtro sigue sin filtrar por nada")
    void sinTexto() {
        when(motoRepository.buscar(any(), any(), eq(true), any())).thenReturn(vacia);

        motoService.buscar("   ", true, PageRequest.of(0, 20));

        verify(motoRepository).buscar(isNull(), isNull(), eq(true), any());
    }
}
