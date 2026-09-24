package com.motorsport19.taller.tiemporeal;

import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Los puestos conectados y el aviso de que algo ha cambiado.
 *
 * <p>El aviso no lleva datos, solo qué parte del programa se ha tocado. Cada
 * puesto vuelve a pedir lo que tiene en pantalla con su propio token, así que
 * un técnico nunca recibe por aquí un precio que la API no le dejaría ver.
 *
 * <p>Los envíos van en un hilo aparte: un puesto con la red lenta no puede
 * frenar la petición de quien ha guardado.
 */
// ponytail: puestos en memoria, vale con una sola instancia del backend; con
// varias, repartir el aviso con LISTEN/NOTIFY de PostgreSQL.
@Component
public class CanalCambios {

    /** Menos que los 120 s de nginx y que el corte por inactividad de Render. */
    private static final long LATIDO_SEGUNDOS = 25;

    private final Set<SseEmitter> puestos = new CopyOnWriteArraySet<>();
    private final ScheduledExecutorService hilo = Executors.newSingleThreadScheduledExecutor();

    public CanalCambios() {
        hilo.scheduleAtFixedRate(this::latido, LATIDO_SEGUNDOS, LATIDO_SEGUNDOS, TimeUnit.SECONDS);
    }

    public SseEmitter abrir() {
        SseEmitter puesto = new SseEmitter(0L);
        puesto.onCompletion(() -> puestos.remove(puesto));
        puesto.onTimeout(() -> puestos.remove(puesto));
        puesto.onError(e -> puestos.remove(puesto));
        puestos.add(puesto);
        return puesto;
    }

    /** @param cliente pestaña que hizo el cambio, para que no se recargue dos veces. */
    public void avisar(String recurso, String cliente) {
        Map<String, String> cambio = Map.of("recurso", recurso, "cliente", cliente == null ? "" : cliente);
        hilo.execute(() -> enviar(SseEmitter.event().name("cambio").data(cambio, MediaType.APPLICATION_JSON)));
    }

    /** Sin tráfico, los proxies cortan la conexión; un comentario la mantiene viva. */
    private void latido() {
        enviar(SseEmitter.event().comment("latido"));
    }

    private void enviar(SseEmitter.SseEventBuilder evento) {
        for (SseEmitter puesto : puestos) {
            try {
                puesto.send(evento);
            } catch (IOException | IllegalStateException e) {
                puestos.remove(puesto);
            }
        }
    }

    @PreDestroy
    void cerrar() {
        hilo.shutdownNow();
        puestos.forEach(SseEmitter::complete);
    }
}
