package com.motorsport19.taller.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Habilita la auditoria de JPA (created_at / created_by / updated_at / updated_by).
 *
 * <p>El {@code AuditorAware} devuelve de momento un valor vacio. En la fase 5 se
 * sustituira por la implementacion que lee el usuario autenticado del contexto de
 * seguridad.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorActual")
public class ConfiguracionAuditoria {

    @Bean
    public AuditorAware<Long> auditorActual() {
        return Optional::empty;
    }
}
