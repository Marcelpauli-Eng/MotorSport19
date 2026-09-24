package com.motorsport19.taller.tiemporeal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroCambiosTest {

    private final List<String> avisos = new ArrayList<>();
    private final FiltroCambios filtro = new FiltroCambios(new CanalCambios() {
        @Override
        public void avisar(String recurso, String cliente) {
            avisos.add(recurso + "|" + cliente);
        }
    });

    private MockHttpServletRequest peticion(String metodo, String ruta, String id) {
        MockHttpServletRequest p = new MockHttpServletRequest(metodo, "/api" + ruta);
        p.setContextPath("/api");
        p.addHeader("X-Cliente", "pestana1");
        if (id != null) p.addHeader("X-Id-Peticion", id);
        return p;
    }

    @Test
    void avisaDelRecursoTrasGuardar() throws Exception {
        filtro.doFilter(peticion("POST", "/inventario/piezas/3/entradas", null),
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(avisos).containsExactly("inventario|pestana1");
    }

    @Test
    void niLecturasNiErroresAvisan() throws Exception {
        filtro.doFilter(peticion("GET", "/piezas", null), new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse rechazada = new MockHttpServletResponse();
        rechazada.setStatus(422);
        filtro.doFilter(peticion("PUT", "/piezas/1", null), rechazada, new MockFilterChain());

        assertThat(avisos).isEmpty();
    }

    @Test
    void unReenvioConElMismoIdNoSeAplicaDosVeces() throws Exception {
        AtomicInteger aplicadas = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            filtro.doFilter(peticion("POST", "/inventario/piezas/3/entradas", "abc"),
                    new MockHttpServletResponse(),
                    (req, res) -> aplicadas.incrementAndGet());
        }

        assertThat(aplicadas).hasValue(1);
        assertThat(avisos).hasSize(1);
    }
}
