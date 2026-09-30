package com.motorsport19.taller.solicitud.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Puerta de las solicitudes de la web")
class FiltroClaveWebTest {

    private static final String CLAVE = "clave-de-la-web-con-al-menos-32-caracteres";

    private static MockHttpServletRequest peticion(String ruta, String clave) {
        var p = new MockHttpServletRequest("POST", "/api" + ruta);
        p.setContextPath("/api");
        if (clave != null) {
            p.addHeader(FiltroClaveWeb.CABECERA, clave);
        }
        return p;
    }

    private static MockHttpServletResponse pasar(FiltroClaveWeb filtro, MockHttpServletRequest p,
                                                 MockFilterChain cadena) throws Exception {
        var respuesta = new MockHttpServletResponse();
        filtro.doFilter(p, respuesta, cadena);
        return respuesta;
    }

    @Test
    @DisplayName("con la clave buena deja pasar")
    void claveBuena() throws Exception {
        var cadena = new MockFilterChain();
        var r = pasar(new FiltroClaveWeb(CLAVE), peticion("/publico/solicitudes-web", CLAVE), cadena);

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("sin clave o con otra, 401 y no llega al controlador")
    void claveMala() throws Exception {
        var filtro = new FiltroClaveWeb(CLAVE);

        var sinClave = new MockFilterChain();
        assertThat(pasar(filtro, peticion("/publico/solicitudes-web", null), sinClave).getStatus()).isEqualTo(401);
        assertThat(sinClave.getRequest()).isNull();

        var otra = new MockFilterChain();
        assertThat(pasar(filtro, peticion("/publico/solicitudes-web", CLAVE + "x"), otra).getStatus()).isEqualTo(401);
        assertThat(otra.getRequest()).isNull();
    }

    @Test
    @DisplayName("sin clave configurada la puerta esta cerrada")
    void cerrada() throws Exception {
        var cadena = new MockFilterChain();
        var r = pasar(new FiltroClaveWeb(""), peticion("/publico/solicitudes-web", CLAVE), cadena);

        assertThat(r.getStatus()).isEqualTo(503);
        assertThat(cadena.getRequest()).isNull();
    }

    @Test
    @DisplayName("el resto de la API no se entera de este filtro")
    void otrasRutas() throws Exception {
        var cadena = new MockFilterChain();
        var r = pasar(new FiltroClaveWeb(""), peticion("/citas", null), cadena);

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("una clave corta impide arrancar")
    void claveCorta() {
        assertThatThrownBy(() -> new FiltroClaveWeb("corta"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 caracteres");
    }
}
