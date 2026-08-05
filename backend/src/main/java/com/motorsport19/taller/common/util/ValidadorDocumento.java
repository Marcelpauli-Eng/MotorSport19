package com.motorsport19.taller.common.util;

import com.motorsport19.taller.cliente.domain.TipoDocumento;

/**
 * Validacion de documentos fiscales espanoles (NIF, NIE y CIF).
 *
 * <p>Un documento mal escrito no se detecta hasta que Hacienda rechaza la
 * factura, cuando ya es tarde y la factura es inmutable. Por eso se comprueba
 * el digito de control en el momento de guardar los datos fiscales.
 */
public final class ValidadorDocumento {

    /** Letras de control del NIF, indexadas por el resto de dividir entre 23. */
    private static final String LETRAS_NIF = "TRWAGMYFPDXBNJZSQVHLCKE";

    /** Letras de control del CIF cuando el control es alfabetico. */
    private static final String LETRAS_CIF = "JABCDEFGHI";

    /** Primeras letras validas de un CIF. */
    private static final String LETRAS_INICIALES_CIF = "ABCDEFGHJNPQRSUVW";

    /** Organizaciones cuyo digito de control es SIEMPRE una letra. */
    private static final String CIF_CONTROL_LETRA = "KPQRSNW";

    /** Organizaciones cuyo digito de control es SIEMPRE un numero. */
    private static final String CIF_CONTROL_NUMERO = "ABEH";

    private ValidadorDocumento() {
    }

    /**
     * Normaliza un documento: sin espacios ni guiones y en mayusculas.
     *
     * @return el documento normalizado, o {@code null} si la entrada era nula o vacia
     */
    public static String normalizar(String documento) {
        if (documento == null) {
            return null;
        }
        String limpio = documento.replaceAll("[\\s.\\-]", "").toUpperCase();
        return limpio.isEmpty() ? null : limpio;
    }

    /**
     * Deduce el tipo de documento a partir de su formato.
     *
     * @return el tipo deducido, o {@link TipoDocumento#OTRO} si no encaja en ninguno
     */
    public static TipoDocumento deducirTipo(String documento) {
        String doc = normalizar(documento);
        if (doc == null || doc.length() != 9) {
            return TipoDocumento.OTRO;
        }
        char inicial = doc.charAt(0);
        if (Character.isDigit(inicial)) {
            return TipoDocumento.NIF;
        }
        if (inicial == 'X' || inicial == 'Y' || inicial == 'Z') {
            return TipoDocumento.NIE;
        }
        if (LETRAS_INICIALES_CIF.indexOf(inicial) >= 0) {
            return TipoDocumento.CIF;
        }
        return TipoDocumento.OTRO;
    }

    /**
     * Indica si el documento tiene forma de NIF, NIE o CIF espanol, es decir, si
     * su digito de control se puede comprobar.
     *
     * <p>Devuelve {@code false} para pasaportes y para numeros de IVA
     * extranjeros: no son invalidos, simplemente no se pueden verificar aqui.
     */
    public static boolean esDocumentoEspanol(String documento) {
        TipoDocumento tipo = deducirTipo(documento);
        return tipo == TipoDocumento.NIF || tipo == TipoDocumento.NIE || tipo == TipoDocumento.CIF;
    }

    /**
     * Comprueba el digito de control del documento.
     *
     * <p>Devuelve {@code false} para los documentos que no son NIF, NIE ni CIF:
     * no llevan digito de control comprobable. Comprueba antes
     * {@link #esDocumentoEspanol(String)} si necesitas distinguir "invalido" de
     * "no verificable".
     */
    public static boolean esValido(String documento) {
        String doc = normalizar(documento);
        if (doc == null) {
            return false;
        }
        return switch (deducirTipo(doc)) {
            case NIF -> esNifValido(doc);
            case NIE -> esNieValido(doc);
            case CIF -> esCifValido(doc);
            case PASAPORTE, OTRO -> false;
        };
    }

    private static boolean esNifValido(String doc) {
        if (!doc.matches("\\d{8}[A-Z]")) {
            return false;
        }
        int numero = Integer.parseInt(doc.substring(0, 8));
        return doc.charAt(8) == LETRAS_NIF.charAt(numero % 23);
    }

    private static boolean esNieValido(String doc) {
        if (!doc.matches("[XYZ]\\d{7}[A-Z]")) {
            return false;
        }
        // La letra inicial se sustituye por su digito equivalente: X=0, Y=1, Z=2.
        int prefijo = "XYZ".indexOf(doc.charAt(0));
        int numero = Integer.parseInt(prefijo + doc.substring(1, 8));
        return doc.charAt(8) == LETRAS_NIF.charAt(numero % 23);
    }

    private static boolean esCifValido(String doc) {
        if (!doc.matches("[" + LETRAS_INICIALES_CIF + "]\\d{7}[\\dA-J]")) {
            return false;
        }
        char inicial = doc.charAt(0);
        String digitos = doc.substring(1, 8);
        char control = doc.charAt(8);

        // Suma de las posiciones pares tal cual, e impares duplicadas sumando sus cifras.
        int suma = 0;
        for (int i = 0; i < digitos.length(); i++) {
            int d = digitos.charAt(i) - '0';
            if (i % 2 == 0) {
                int doble = d * 2;
                suma += doble / 10 + doble % 10;
            } else {
                suma += d;
            }
        }
        int digitoControl = (10 - suma % 10) % 10;

        if (CIF_CONTROL_LETRA.indexOf(inicial) >= 0) {
            return control == LETRAS_CIF.charAt(digitoControl);
        }
        if (CIF_CONTROL_NUMERO.indexOf(inicial) >= 0) {
            return control == (char) ('0' + digitoControl);
        }
        // El resto admite indistintamente digito o letra.
        return control == (char) ('0' + digitoControl) || control == LETRAS_CIF.charAt(digitoControl);
    }
}
