package com.motorsport19.taller.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita la auditoria de JPA (created_at / created_by / updated_at / updated_by).
 *
 * <p>Quien resuelve el usuario es el bean {@code auditorActual} que define
 * {@link com.motorsport19.taller.seguridad.ConfiguracionAuditor}, y que lee el
 * usuario autenticado del contexto de seguridad. Hasta la fase 5 ese proveedor
 * devolvia siempre vacio y las columnas de firma quedaban a nulo.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorActual")
public class ConfiguracionAuditoria {
}
