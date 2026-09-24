package com.motorsport19.taller.fichaje.service;

import com.motorsport19.taller.seguridad.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Apunta en la bitacora ({@code actividad_usuario}) el trabajo que no deja
 * rastro por si solo: escribir un diagnostico, meter una linea, cambiar un
 * precio, editar una ficha.
 *
 * <p>Va dentro de la misma transaccion que la operacion: si esta falla, el
 * apunte tampoco queda, y si queda la operacion, queda el apunte.
 */
@Service
public class RegistroActividad {

    private final JdbcClient jdbc;
    private final UsuarioActual usuarioActual;

    public RegistroActividad(JdbcClient jdbc, UsuarioActual usuarioActual) {
        this.jdbc = jdbc;
        this.usuarioActual = usuarioActual;
    }

    /**
     * @param tipo        etiqueta que se ve en la lista (OT, CLIENTE, MOTO, ALMACEN...)
     * @param destinoTipo a donde lleva el apunte: orden, cliente, moto, pieza
     */
    public void anotar(String tipo, String destinoTipo, Long destinoId, String texto) {
        Long usuarioId = usuarioActual.id();
        if (usuarioId == null) {
            return;   // procesos internos sin nadie detras
        }
        String recortado = texto.length() > 500 ? texto.substring(0, 497) + "..." : texto;
        jdbc.sql("""
                INSERT INTO actividad_usuario (usuario_id, tipo, destino_tipo, destino_id, texto)
                VALUES (?, ?, ?, ?, ?)
                """)
                .params(usuarioId, tipo, destinoTipo, destinoId, recortado)
                .update();
    }
}
