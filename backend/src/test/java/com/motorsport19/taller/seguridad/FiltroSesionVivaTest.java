package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.usuario.domain.Permiso;
import com.motorsport19.taller.usuario.domain.Rol;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Que una baja o un cambio de permisos surtan efecto en el acto.
 *
 * <p>El token va firmado y lleva dentro los permisos, asi que autorizar salia
 * gratis. La contrapartida es que dura ocho horas, y en ese rato pasan cosas: se
 * despide a alguien, se le retira una atribucion. Antes nada de eso afectaba a
 * quien ya estaba dentro —seguia listando y creando datos toda la tarde— y solo
 * se notaba al dia siguiente, cuando volvia a entrar.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Sesion viva")
class FiltroSesionVivaTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private FilterChain cadena;

    private FiltroSesionViva filtro;
    private MockHttpServletRequest peticion;
    private MockHttpServletResponse respuesta;

    @BeforeEach
    void preparar() {
        filtro = new FiltroSesionViva(usuarios);
        peticion = new MockHttpServletRequest("GET", "/api/clientes");
        respuesta = new MockHttpServletResponse();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    /** Deja en el contexto un token como el que llega de la cabecera. */
    private void conTokenDe(long usuarioId, String... permisosDelToken) {
        Jwt token = Jwt.withTokenValue("da-igual")
                .header("alg", "HS256")
                .subject("pau")
                .claim("uid", usuarioId)
                .claim("permisos", List.of(permisosDelToken))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        var autenticacion = new TestingAuthenticationToken(token, null,
                java.util.Arrays.stream(permisosDelToken)
                        .map(SimpleGrantedAuthority::new).toList());
        autenticacion.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
    }

    private Usuario usuarioCon(Set<Permiso> permisos, boolean activo) {
        Rol rol = Rol.crear("Mostrador", "El de la barra", EnumSet.copyOf(permisos));
        ReflectionTestUtils.setField(rol, "id", 7L);
        Usuario usuario = Usuario.crear("pau", "$2a$10$hash", "Pau Tecnico", null, null, rol);
        ReflectionTestUtils.setField(usuario, "id", 5L);
        if (!activo) {
            usuario.darDeBaja();
        }
        return usuario;
    }

    @Test
    @DisplayName("un usuario dado de baja no pasa, aunque su token siga vigente")
    void bajaEnCaliente() throws Exception {
        conTokenDe(5L, "CLIENTES_VER", "CLIENTES_CREAR");
        when(usuarios.findById(5L))
                .thenReturn(Optional.of(usuarioCon(EnumSet.of(Permiso.CLIENTES_VER), false)));

        filtro.doFilter(peticion, respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(401);
        // No llega al controlador: no puede crear, ni leer, ni nada.
        verify(cadena, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("un usuario borrado tampoco")
    void usuarioQueYaNoEsta() throws Exception {
        conTokenDe(5L, "CLIENTES_VER");
        when(usuarios.findById(5L)).thenReturn(Optional.empty());

        filtro.doFilter(peticion, respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(401);
        verify(cadena, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("manda el permiso que tiene ahora, no el que llevaba el token")
    void permisoRetiradoEnCaliente() throws Exception {
        // El token se emitio cuando podia crear clientes; despues se le quito.
        conTokenDe(5L, "CLIENTES_VER", "CLIENTES_CREAR");
        when(usuarios.findById(5L))
                .thenReturn(Optional.of(usuarioCon(EnumSet.of(Permiso.CLIENTES_VER), true)));

        filtro.doFilter(peticion, respuesta, cadena);

        verify(cadena).doFilter(peticion, respuesta);
        var autoridades = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertThat(autoridades).extracting(Object::toString)
                .containsExactly("CLIENTES_VER")
                .doesNotContain("CLIENTES_CREAR");
    }

    @Test
    @DisplayName("y tambien el que se le acaba de dar")
    void permisoConcedidoEnCaliente() throws Exception {
        conTokenDe(5L, "CLIENTES_VER");
        when(usuarios.findById(5L)).thenReturn(Optional.of(
                usuarioCon(EnumSet.of(Permiso.CLIENTES_VER, Permiso.FACTURAS_EMITIR), true)));

        filtro.doFilter(peticion, respuesta, cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .contains("FACTURAS_EMITIR");
    }

    @Test
    @DisplayName("el usuario que sigue de alta pasa y llega como UsuarioAutenticado")
    void usuarioNormal() throws Exception {
        conTokenDe(5L, "CLIENTES_VER");
        when(usuarios.findById(5L))
                .thenReturn(Optional.of(usuarioCon(EnumSet.of(Permiso.CLIENTES_VER), true)));

        filtro.doFilter(peticion, respuesta, cadena);

        verify(cadena).doFilter(peticion, respuesta);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(UsuarioAutenticado.class);
    }

    @Test
    @DisplayName("una peticion sin token pasa de largo: del acceso ya se ocupan las rutas")
    void sinToken() throws Exception {
        filtro.doFilter(peticion, respuesta, cadena);

        verify(cadena).doFilter(peticion, respuesta);
        verify(usuarios, never()).findById(any());
    }
}
