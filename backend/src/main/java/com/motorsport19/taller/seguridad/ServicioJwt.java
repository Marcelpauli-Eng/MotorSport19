package com.motorsport19.taller.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Emision de los tokens de sesion.
 *
 * <p>El token lleva dentro el identificador, el nombre y el rol del usuario, de
 * modo que cada peticion se resuelve sin ir a la base de datos. Va firmado con
 * HMAC-SHA256: cualquier cambio en su contenido invalida la firma.
 *
 * <p>La duracion es deliberadamente corta para lo que suele verse en estos
 * sistemas. Un token robado sirve mientras no caduca y no hay forma de
 * revocarlo, asi que la ventana de dano la marca su vigencia.
 */
@Service
public class ServicioJwt {

    /** Emisor que se graba en el token. */
    private static final String EMISOR = "motorsport19";

    private final JwtEncoder codificador;
    private final Duration duracion;

    public ServicioJwt(JwtEncoder codificador,
                       @Value("${motorsport19.seguridad.duracion-token:PT8H}") Duration duracion) {
        this.codificador = codificador;
        this.duracion = duracion;
    }

    /**
     * Genera el token de un usuario recien autenticado.
     *
     * @return el token firmado y el instante en que caduca
     */
    public TokenEmitido emitir(UsuarioAutenticado usuario) {
        Instant ahora = Instant.now();
        Instant caducidad = ahora.plus(duracion);

        JwtClaimsSet datos = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .issuedAt(ahora)
                .expiresAt(caducidad)
                .subject(usuario.username())
                .claim("uid", usuario.id())
                .claim("nombre", usuario.nombreCompleto())
                .claim("rol", usuario.rol().name())
                .build();

        String token = codificador
                .encode(JwtEncoderParameters.from(JwsHeader.with(() -> "HS256").build(), datos))
                .getTokenValue();

        return new TokenEmitido(token, caducidad, duracion.toSeconds());
    }

    /**
     * @param token      valor a enviar en la cabecera Authorization
     * @param caduca     instante exacto de caducidad
     * @param duracionSegundos vigencia total, para que el frontend sepa cuando renovar
     */
    public record TokenEmitido(String token, Instant caduca, long duracionSegundos) {
    }
}
