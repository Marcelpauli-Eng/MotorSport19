package com.motorsport19.taller.solicitud.domain;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Foto de una solicitud")
class FotoSolicitudTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P'};

    @Test
    @DisplayName("reconoce JPEG, PNG y WebP por sus primeros bytes")
    void reconoceLosFormatos() {
        assertThat(FotoSolicitud.tipoDe(JPEG)).isEqualTo("image/jpeg");
        assertThat(FotoSolicitud.tipoDe(PNG)).isEqualTo("image/png");
        assertThat(FotoSolicitud.tipoDe(WEBP)).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("lo demas no es una foto, diga lo que diga el nombre")
    void rechazaLoDemas() {
        assertThat(FotoSolicitud.tipoDe("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isNull();
        assertThat(FotoSolicitud.tipoDe("<svg onload=alert(1)>".getBytes(StandardCharsets.US_ASCII))).isNull();

        SolicitudWeb s = SolicitudWebTest.solicitud(TipoSolicitud.PRESUPUESTO);
        assertThatThrownBy(() -> FotoSolicitud.de(s, 1, "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("JPEG, PNG o WebP");
    }

    @Test
    @DisplayName("tope de 3 MB por foto")
    void tamanoMaximo() {
        byte[] grande = new byte[FotoSolicitud.TAMANO_MAXIMO + 1];
        System.arraycopy(JPEG, 0, grande, 0, JPEG.length);
        SolicitudWeb s = SolicitudWebTest.solicitud(TipoSolicitud.PRESUPUESTO);

        assertThatThrownBy(() -> FotoSolicitud.de(s, 1, grande))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("3 MB");
        assertThat(FotoSolicitud.de(s, 2, JPEG).getTipoContenido()).isEqualTo("image/jpeg");
    }
}
