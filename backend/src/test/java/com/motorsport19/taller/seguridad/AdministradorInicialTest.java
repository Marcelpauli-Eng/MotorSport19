package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.support.RolesDePrueba;
import com.motorsport19.taller.usuario.domain.Rol;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import com.motorsport19.taller.usuario.service.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del arranque en una instalacion nueva.
 *
 * <p>Lo que se protege aqui es que una base vacia no quede inservible (nadie
 * podria entrar a crear al primer usuario) y, sobre todo, que un reinicio de una
 * instalacion en marcha no toque las cuentas existentes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Administrador inicial")
class AdministradorInicialTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private com.motorsport19.taller.usuario.repository.RolRepository rolRepository;
    @Mock private UsuarioService usuarioService;

    private AdministradorInicial conConfiguracion(String username, String password) {
        return new AdministradorInicial(usuarioRepository, rolRepository, usuarioService, username, password);
    }

    @Test
    @DisplayName("con la base vacia crea un administrador con contrasena aleatoria")
    void baseVacia() {
        when(usuarioRepository.count()).thenReturn(0L);
        when(rolRepository.findById(Rol.ID_ADMINISTRACION))
                .thenReturn(java.util.Optional.of(RolesDePrueba.administracion()));

        conConfiguracion("", "").run(null);

        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(usuarioService).crear(eq("admin"), password.capture(), any(), any(), any(),
                eq(Rol.ID_ADMINISTRACION));
        assertThat(password.getValue()).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    @DisplayName("dos arranques seguidos generan contrasenas distintas")
    void passwordNoPredecible() {
        when(usuarioRepository.count()).thenReturn(0L);
        when(rolRepository.findById(Rol.ID_ADMINISTRACION))
                .thenReturn(java.util.Optional.of(RolesDePrueba.administracion()));

        conConfiguracion("", "").run(null);
        conConfiguracion("", "").run(null);

        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(usuarioService, org.mockito.Mockito.times(2))
                .crear(any(), password.capture(), any(), any(), any(), any());
        assertThat(password.getAllValues().get(0)).isNotEqualTo(password.getAllValues().get(1));
    }

    @Test
    @DisplayName("respeta la contrasena configurada si se ha indicado")
    void passwordConfigurada() {
        when(usuarioRepository.count()).thenReturn(0L);
        when(rolRepository.findById(Rol.ID_ADMINISTRACION))
                .thenReturn(java.util.Optional.of(RolesDePrueba.administracion()));

        conConfiguracion("direccion", "unaClaveElegida2026").run(null);

        verify(usuarioService).crear(eq("direccion"), eq("unaClaveElegida2026"), any(), any(), any(),
                eq(Rol.ID_ADMINISTRACION));
    }

    @Test
    @DisplayName("si ya hay usuarios no toca nada")
    void instalacionEnMarcha() {
        when(usuarioRepository.count()).thenReturn(4L);

        conConfiguracion("", "").run(null);

        verify(usuarioService, never()).crear(any(), any(), any(), any(), any(), any());
    }
}
