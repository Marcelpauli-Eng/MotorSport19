package com.motorsport19.taller.agenda.web.dto;

import com.motorsport19.taller.agenda.domain.Cita;
import com.motorsport19.taller.agenda.domain.EstadoCita;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Cita tal y como la pinta el calendario.
 *
 * <p>{@code contacto} y {@code moto} salen resueltos: cuando la moto esta en el
 * sistema mandan sus datos, y si no, los que se apuntaron a mano al coger la
 * cita por telefono. Quien pinta el calendario no tiene por que saber de cual de
 * los dos sitios viene cada cosa.
 */
public record CitaResponse(
        Long id,
        Instant fechaHora,
        BigDecimal duracionEstimada,
        EstadoCita estado,
        String estadoDescripcion,
        Set<EstadoCita> estadosPosibles,

        Long motoId,
        String matricula,
        /** Descripcion de la moto, del sistema o escrita a mano. */
        String moto,
        Long clienteId,
        String contactoNombre,
        String contactoTelefono,
        /** Si la moto todavia no esta dada de alta. */
        boolean motoSinRegistrar,

        String motivo,
        Long tecnicoId,
        String tecnicoNombre,
        String observaciones,

        Long ordenTrabajoId,
        String ordenCodigo,
        String motivoCancelacion
) {

    public static CitaResponse de(Cita cita) {
        Moto moto = cita.getMoto();
        OrdenTrabajo orden = cita.getOrdenTrabajo();

        return new CitaResponse(
                cita.getId(),
                cita.getFechaHora(),
                cita.getDuracionEstimada(),
                cita.getEstado(),
                cita.getEstado().getDescripcion(),
                cita.getEstado().siguientesPosibles(),

                moto == null ? null : moto.getId(),
                moto == null ? null : moto.getMatricula(),
                cita.moto(),
                moto == null || moto.getCliente() == null ? null : moto.getCliente().getId(),
                cita.nombreDeContacto(),
                cita.telefonoDeContacto(),
                moto == null,

                cita.getMotivo(),
                cita.getTecnico() == null ? null : cita.getTecnico().getId(),
                cita.getTecnico() == null ? null : cita.getTecnico().getNombreCompleto(),
                cita.getObservaciones(),

                orden == null ? null : orden.getId(),
                orden == null ? null : orden.codigoVisible(),
                cita.getMotivoCancelacion());
    }
}
