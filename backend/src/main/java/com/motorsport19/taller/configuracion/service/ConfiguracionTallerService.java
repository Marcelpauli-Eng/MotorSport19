package com.motorsport19.taller.configuracion.service;

import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.configuracion.repository.ConfiguracionTallerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Acceso a la fila unica de configuracion del taller. */
@Service
public class ConfiguracionTallerService {

    private final ConfiguracionTallerRepository repositorio;

    public ConfiguracionTallerService(ConfiguracionTallerRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * La configuracion, o un error claro si falta.
     *
     * <p>Sin ella no se puede emitir nada: los datos del emisor van impresos en
     * cada documento y copiados dentro de cada factura.
     */
    @Transactional(readOnly = true)
    public ConfiguracionTaller obligatoria() {
        return repositorio.findById(ConfiguracionTaller.ID_UNICO)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Faltan los datos del taller: rellenelos en Ajustes > Empresa y facturacion."));
    }
}
