package com.motorsport19.taller.solicitud.web.dto;

import com.motorsport19.taller.solicitud.domain.TipoSolicitud;
import com.motorsport19.taller.solicitud.service.SolicitudWebService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que manda la web por cada formulario enviado.
 *
 * <p>La web ya lo valida antes de mandarlo; se vuelve a validar aqui porque es
 * la puerta de la base de datos, y una puerta no se fia de quien llama.
 */
public record NuevaSolicitudRequest(
        @NotBlank @Size(max = 40) String referencia,
        @NotNull TipoSolicitud tipo,
        @NotBlank @Pattern(regexp = "es|ca|en|fr") String idioma,
        @NotBlank @Size(max = 120) String nombre,
        @NotBlank @Pattern(regexp = "\\+?[0-9\\s().-]{9,20}") String telefono,
        @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 60) String marca,
        @NotBlank @Size(max = 60) String modelo,
        @Size(max = 20) String matricula,
        @NotBlank @Size(max = 3000) String necesita,
        LocalDate fechaPreferida,
        /** En base64, como mucho tres. */
        @Size(max = 3) List<@NotBlank String> fotos) {

    public SolicitudWebService.Entrada entrada() {
        return new SolicitudWebService.Entrada(referencia, tipo, idioma, nombre, telefono, email,
                marca, modelo, matricula, necesita, fechaPreferida, fotos);
    }
}
