package com.motorsport19.taller.factura.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Generacion del codigo QR de verificacion que se imprime en la factura.
 */
public final class GeneradorQr {

    /** Lado del QR en pixeles. Suficiente para imprimir nitido a ~35 mm. */
    private static final int TAMANO = 400;

    private GeneradorQr() {
    }

    /**
     * Codifica el contenido en un PNG.
     *
     * <p>Se usa correccion de errores nivel M (recupera ~15%): una factura en
     * papel acaba doblada, manchada de grasa o fotocopiada, y el QR tiene que
     * seguir leyendose.
     */
    public static byte[] generarPng(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("El contenido del QR no puede estar vacio.");
        }
        try {
            BitMatrix matriz = new QRCodeWriter().encode(
                    contenido, BarcodeFormat.QR_CODE, TAMANO, TAMANO,
                    Map.of(
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                            EncodeHintType.MARGIN, 1));

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matriz, "PNG", salida);
            return salida.toByteArray();

        } catch (WriterException | IOException e) {
            throw new IllegalStateException("No se ha podido generar el QR de la factura", e);
        }
    }
}
