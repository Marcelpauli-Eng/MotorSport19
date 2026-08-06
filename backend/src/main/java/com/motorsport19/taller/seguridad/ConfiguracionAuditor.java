package com.motorsport19.taller.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * Rellena {@code created_by} y {@code updated_by} con el usuario autenticado.
 *
 * <p>Sustituye al proveedor vacio de la fase 1. A partir de ahora, cada fila que
 * se crea o se modifica queda firmada de verdad: hasta esta fase, las columnas
 * de auditoria estaban ahi pero siempre vacias.
 *
 * <p>La clase se llama {@code ConfiguracionAuditor} y no {@code AuditorActual}
 * porque Spring nombra el bean de configuracion con el nombre de la clase en
 * minusculas, y chocaria con el bean que declara dentro.
 */
@Configuration
public class ConfiguracionAuditor {

    @Bean
    public AuditorAware<Long> auditorActual(UsuarioActual usuarioActual) {
        // Las migraciones y las tareas de arranque no tienen usuario: en esos
        // casos la columna se queda a nulo, que es lo correcto.
        return () -> Optional.ofNullable(usuarioActual.id());
    }
}
