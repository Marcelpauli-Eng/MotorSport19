package com.motorsport19.taller.tiemporeal;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Conexión abierta por la que cada puesto se entera de los cambios de los demás. */
@RestController
public class EventosController {

    private final CanalCambios canal;

    public EventosController(CanalCambios canal) {
        this.canal = canal;
    }

    @GetMapping(path = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos(HttpServletResponse respuesta) {
        // Sin esto nginx guarda los eventos en su búfer y llegan tarde o nunca.
        respuesta.setHeader("X-Accel-Buffering", "no");
        respuesta.setHeader("Cache-Control", "no-cache");
        return canal.abrir();
    }
}
