package com.motorsport19.taller.tiemporeal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cualquier cambio que la API acepte se avisa a todos los puestos.
 *
 * <p>Va en un filtro y no en cada controlador para que no se pueda olvidar: una
 * pantalla nueva que guarde algo avisa sola. Corre detrás de la seguridad, así
 * que solo cuenta lo que de verdad se ha guardado (respuesta 2xx).
 *
 * <p>También evita aplicar dos veces la misma petición. Sin conexión, el
 * navegador guarda lo que se hace y lo reenvía al volver; si la primera vez sí
 * llegó y lo que se perdió fue la respuesta, reenviarla daría dos entradas de
 * almacén en vez de una. Cada petición lleva un identificador y aquí se
 * recuerdan los ya aplicados.
 */
// ponytail: identificadores en memoria, se olvidan al reiniciar; si molesta,
// guardarlos en una tabla con caducidad.
@Component
public class FiltroCambios extends OncePerRequestFilter {

    static final String CABECERA_CLIENTE = "X-Cliente";
    static final String CABECERA_ID = "X-Id-Peticion";

    private static final Set<String> SIN_CAMBIOS = Set.of("GET", "HEAD", "OPTIONS");
    private static final int RECORDADAS = 5000;

    private final CanalCambios canal;
    private final Set<String> aplicadas = Collections.newSetFromMap(Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> mayor) {
                    return size() > RECORDADAS;
                }
            }));

    public FiltroCambios(CanalCambios canal) {
        this.canal = canal;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        String ruta = rutaDe(peticion);
        return SIN_CAMBIOS.contains(peticion.getMethod()) || ruta.startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        String id = peticion.getHeader(CABECERA_ID);
        if (id != null && aplicadas.contains(id)) {
            respuesta.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        cadena.doFilter(peticion, respuesta);

        if (respuesta.getStatus() < 200 || respuesta.getStatus() >= 300) {
            return;
        }
        if (id != null) {
            aplicadas.add(id);
        }
        canal.avisar(recursoDe(peticion), peticion.getHeader(CABECERA_CLIENTE));
    }

    /** «/ordenes/12/lineas» → «ordenes». */
    static String recursoDe(HttpServletRequest peticion) {
        String ruta = rutaDe(peticion);
        int fin = ruta.indexOf('/', 1);
        return fin < 0 ? ruta.substring(1) : ruta.substring(1, fin);
    }

    private static String rutaDe(HttpServletRequest peticion) {
        return peticion.getRequestURI().substring(peticion.getContextPath().length());
    }
}
