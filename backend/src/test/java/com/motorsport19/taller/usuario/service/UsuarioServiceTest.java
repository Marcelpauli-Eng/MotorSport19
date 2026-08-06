package com.motorsport19.taller.usuario.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.usuario.domain.Rol;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Servicio de usuarios")
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    /** Se usa el codificador de verdad: probar BCrypt contra un mock no prueba nada. */
    private final PasswordEncoder codificador = new BCryptPasswordEncoder(4);

    private UsuarioService usuarioService;

    @BeforeEach
    void preparar() {
        usuarioService = new UsuarioService(usuarioRepository, codificador);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Usuario usuarioCon(Long id, String username, Rol rol, String password) {
        Usuario u = Usuario.crear(username, codificador.encode(password), "Nombre Apellido",
                null, null, rol);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("la contrasena se guarda como hash, nunca en claro")
        void passwordHasheada() {
            when(usuarioRepository.findByUsername("nuevo")).thenReturn(Optional.empty());

            Usuario usuario = usuarioService.crear("nuevo", "contrasena123", "Nuevo Usuario",
                    null, null, Rol.TECNICO);

            assertThat(usuario.getPasswordHash())
                    .doesNotContain("contrasena123")
                    .startsWith("$2");
            assertThat(codificador.matches("contrasena123", usuario.getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("normaliza el nombre de usuario a minusculas")
        void usernameEnMinusculas() {
            when(usuarioRepository.findByUsername("jortega")).thenReturn(Optional.empty());

            Usuario usuario = usuarioService.crear("  JOrtega ", "contrasena123", "Javier Ortega",
                    null, null, Rol.TECNICO);

            // Sin esto existirian "jortega" y "JOrtega" como usuarios distintos.
            assertThat(usuario.getUsername()).isEqualTo("jortega");
        }

        @Test
        @DisplayName("rechaza un nombre de usuario ya existente")
        void usernameDuplicado() {
            when(usuarioRepository.findByUsername("admin"))
                    .thenReturn(Optional.of(usuarioCon(1L, "admin", Rol.ADMIN, "loquesea1")));

            assertThatThrownBy(() -> usuarioService.crear("admin", "contrasena123", "Otro",
                    null, null, Rol.MOSTRADOR))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("admin");
        }

        @Test
        @DisplayName("rechaza contrasenas demasiado cortas")
        void passwordCorta() {
            when(usuarioRepository.findByUsername(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> usuarioService.crear("nuevo", "1234", "Nuevo",
                    null, null, Rol.TECNICO))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("8 caracteres");
        }
    }

    @Nested
    @DisplayName("Cambio de contrasena")
    class CambioPassword {

        @Test
        @DisplayName("exige la contrasena actual")
        void exigeLaActual() {
            Usuario usuario = usuarioCon(1L, "jortega", Rol.TECNICO, "actual12345");
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

            // Si no se pidiera, una sesion olvidada en el mostrador permitiria
            // apropiarse de la cuenta.
            assertThatThrownBy(() -> usuarioService.cambiarPassword(1L, "equivocada", "nueva12345"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("actual no es correcta");
        }

        @Test
        @DisplayName("cambia la contrasena cuando la actual es correcta")
        void cambiaCorrectamente() {
            Usuario usuario = usuarioCon(1L, "jortega", Rol.TECNICO, "actual12345");
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

            usuarioService.cambiarPassword(1L, "actual12345", "nueva123456");

            assertThat(codificador.matches("nueva123456", usuario.getPasswordHash())).isTrue();
            assertThat(codificador.matches("actual12345", usuario.getPasswordHash())).isFalse();
        }

        @Test
        @DisplayName("no acepta repetir la misma contrasena")
        void mismaContrasena() {
            Usuario usuario = usuarioCon(1L, "jortega", Rol.TECNICO, "actual12345");
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

            assertThatThrownBy(() -> usuarioService.cambiarPassword(1L, "actual12345", "actual12345"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("distinta");
        }
    }

    @Nested
    @DisplayName("Baja")
    class Baja {

        @Test
        @DisplayName("nadie puede darse de baja a si mismo")
        void bajaPropia() {
            Usuario admin = usuarioCon(1L, "admin", Rol.ADMIN, "admin12345");
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> usuarioService.darDeBaja(1L, 1L))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("a si mismo");
        }

        @Test
        @DisplayName("no se puede dar de baja al ultimo administrador")
        void ultimoAdministrador() {
            Usuario admin = usuarioCon(1L, "admin", Rol.ADMIN, "admin12345");
            Usuario tecnico = usuarioCon(2L, "jortega", Rol.TECNICO, "tecnico1234");
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(usuarioRepository.findAll()).thenReturn(List.of(admin, tecnico));

            // Dejar el sistema sin ningun administrador significa que nadie
            // puede volver a crear usuarios ni tocar la configuracion fiscal.
            assertThatThrownBy(() -> usuarioService.darDeBaja(1L, 99L))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("ultimo administrador");
        }

        @Test
        @DisplayName("sí se puede dar de baja a un administrador si queda otro")
        void administradorConRelevo() {
            Usuario admin1 = usuarioCon(1L, "admin", Rol.ADMIN, "admin12345");
            Usuario admin2 = usuarioCon(2L, "jefe", Rol.ADMIN, "jefe123456");
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin1));
            when(usuarioRepository.findAll()).thenReturn(List.of(admin1, admin2));

            usuarioService.darDeBaja(1L, 99L);

            assertThat(admin1.isActivo()).isFalse();
        }

        @Test
        @DisplayName("un usuario dado de baja no puede entrar")
        void bajaImpideAcceso() {
            Usuario usuario = usuarioCon(1L, "jortega", Rol.TECNICO, "tecnico1234");
            usuario.darDeBaja();

            // El usuario sigue existiendo porque firma ordenes del pasado, pero
            // Spring Security lo rechaza al comprobar isEnabled().
            assertThat(UsuarioAutenticadoDePrueba.de(usuario).isEnabled()).isFalse();
        }
    }

    /** Atajo para no importar la clase de seguridad en todo el fichero. */
    private static final class UsuarioAutenticadoDePrueba {
        static com.motorsport19.taller.seguridad.UsuarioAutenticado de(Usuario u) {
            return com.motorsport19.taller.seguridad.UsuarioAutenticado.de(u);
        }
    }
}
