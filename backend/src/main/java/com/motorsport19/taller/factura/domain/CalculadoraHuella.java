package com.motorsport19.taller.factura.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Calculo de la huella encadenada de las facturas.
 *
 * <p>Cada factura incorpora a su huella la huella de la anterior, de modo que las
 * facturas emitidas forman una cadena: alterar o eliminar una rompe todas las
 * posteriores, y eso se detecta recalculando. La primera factura encadena con la
 * huella genesis (64 ceros).
 *
 * <p>La cadena canonica sigue el formato del registro de facturacion espanol:
 * <pre>
 * NIFEmisor=B87654323&amp;NumSerieFactura=A/2026/000001&amp;FechaExpedicion=15-05-2026
 * &amp;TipoFactura=ORDINARIA&amp;CuotaTotal=42.48&amp;ImporteTotal=244.68
 * &amp;Huella=&lt;huella anterior&gt;&amp;FechaHoraHusoGenRegistro=2026-05-15T18:25:00+02
 * </pre>
 *
 * <p>El texto exacto se guarda en la propia factura ({@code cadena_huella}), asi
 * que la huella se puede reverificar dentro de anos aunque este codigo cambie:
 * basta con volver a pasar por SHA-256 la cadena almacenada.
 */
public final class CalculadoraHuella {

    public static final String ALGORITMO = "SHA-256";

    /** Huella con la que arranca la cadena: 64 ceros. */
    public static final String HUELLA_GENESIS = "0".repeat(64);

    /** El taller opera en Espana; las fechas del registro van en hora local. */
    public static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private static final DateTimeFormatter FECHA_EXPEDICION = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private CalculadoraHuella() {
    }

    /**
     * Compone la cadena canonica que se pasara por SHA-256.
     *
     * @param nifEmisor       NIF del taller
     * @param numeroCompleto  numero visible de la factura (A/2026/000001)
     * @param fechaEmision    fecha de expedicion
     * @param tipo            ORDINARIA o RECTIFICATIVA
     * @param cuotaTotal      suma de cuotas de IVA
     * @param importeTotal    total de la factura
     * @param huellaAnterior  huella de la factura precedente, o la genesis
     * @param instanteEmision momento exacto del registro
     */
    public static String cadenaCanonica(String nifEmisor, String numeroCompleto, LocalDate fechaEmision,
                                        TipoFactura tipo, BigDecimal cuotaTotal, BigDecimal importeTotal,
                                        String huellaAnterior, Instant instanteEmision) {
        return "NIFEmisor=%s&NumSerieFactura=%s&FechaExpedicion=%s&TipoFactura=%s&CuotaTotal=%s"
                .formatted(nifEmisor, numeroCompleto, FECHA_EXPEDICION.format(fechaEmision), tipo.name(),
                        importe(cuotaTotal))
                + "&ImporteTotal=%s&Huella=%s&FechaHoraHusoGenRegistro=%s"
                .formatted(importe(importeTotal), huellaAnterior, marcaTemporal(instanteEmision));
    }

    /** SHA-256 en hexadecimal minusculas de la cadena canonica. */
    public static String calcular(String cadenaCanonica) {
        try {
            byte[] digest = MessageDigest.getInstance(ALGORITMO)
                    .digest(cadenaCanonica.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, el entorno esta roto.
            throw new IllegalStateException("La JVM no soporta " + ALGORITMO, e);
        }
    }

    /** Comprueba que una huella corresponde a la cadena canonica almacenada. */
    public static boolean huellaCoincide(String cadenaCanonica, String huella) {
        return huella != null && huella.equalsIgnoreCase(calcular(cadenaCanonica));
    }

    /** Importe con dos decimales y punto como separador, sin separador de miles. */
    private static String importe(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Marca temporal en hora local con su huso.
     *
     * <p>El desplazamiento se escribe como {@code +02} y no como {@code +02:00}
     * para reproducir exactamente el formato {@code OF} de {@code to_char} de
     * PostgreSQL, que es con el que se sello la cadena inicial. Si los dos
     * formatos no coincidieran, las huellas calculadas desde Java y desde SQL
     * serian distintas y la cadena no verificaria.
     */
    static String marcaTemporal(Instant instante) {
        ZonedDateTime local = instante.atZone(ZONA);
        int segundosOffset = local.getOffset().getTotalSeconds();

        String signo = segundosOffset < 0 ? "-" : "+";
        int absoluto = Math.abs(segundosOffset);
        int horas = absoluto / 3600;
        int minutos = (absoluto % 3600) / 60;

        String desplazamiento = minutos == 0
                ? "%s%02d".formatted(signo, horas)
                : "%s%02d:%02d".formatted(signo, horas, minutos);

        return FECHA_HORA.format(local) + desplazamiento;
    }
}
