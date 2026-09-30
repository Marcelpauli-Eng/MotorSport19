package com.motorsport19.taller.solicitud.domain;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Foto que adjunta el cliente a su solicitud.
 *
 * <p>No es una coleccion de {@link SolicitudWeb} a proposito: cargar la bandeja
 * traeria las imagenes enteras de cada solicitud solo para listarlas. Se piden
 * de una en una, cuando alguien las va a mirar.
 */
@Entity
@Table(name = "solicitud_web_foto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FotoSolicitud {

    /** Una foto de movil reducida por la web ronda 300 KB; esto deja margen de sobra. */
    public static final int TAMANO_MAXIMO = 3 * 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false, updatable = false)
    private SolicitudWeb solicitud;

    /** 1, 2 o 3: el orden en que las adjunto el cliente. */
    @Column(name = "orden", nullable = false, updatable = false)
    private short orden;

    @Column(name = "tipo_contenido", nullable = false, length = 20, updatable = false)
    private String tipoContenido;

    @Column(name = "datos", nullable = false, updatable = false)
    private byte[] datos;

    public static FotoSolicitud de(SolicitudWeb solicitud, int orden, byte[] datos) {
        if (datos == null || datos.length == 0) {
            throw new ReglaNegocioException("La foto %d llego vacia.".formatted(orden));
        }
        if (datos.length > TAMANO_MAXIMO) {
            throw new ReglaNegocioException("La foto %d pasa de 3 MB.".formatted(orden));
        }
        String tipo = tipoDe(datos);
        if (tipo == null) {
            throw new ReglaNegocioException("La foto %d no es una imagen JPEG, PNG o WebP.".formatted(orden));
        }
        FotoSolicitud foto = new FotoSolicitud();
        foto.solicitud = solicitud;
        foto.orden = (short) orden;
        foto.tipoContenido = tipo;
        foto.datos = datos;
        return foto;
    }

    /**
     * Tipo de imagen segun sus primeros bytes, o nulo si no es ninguna admitida.
     *
     * <p>Se mira el contenido y no lo que diga quien lo envia: la cabecera de un
     * fichero se escribe a mano, sus bytes no mienten.
     */
    static String tipoDe(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
