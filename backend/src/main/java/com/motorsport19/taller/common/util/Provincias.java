package com.motorsport19.taller.common.util;

import java.util.Map;
import java.util.Optional;

/**
 * La provincia que corresponde a un codigo postal espanol.
 *
 * <p>Los dos primeros digitos de un codigo postal espanol <b>son</b> el codigo de
 * provincia, sin excepciones. Por eso esto es una tabla fija de 52 entradas y no
 * una consulta: no hace falta red, no falla nunca, y responde en cuanto se
 * teclea el segundo digito, cuando el usuario todavia esta escribiendo el resto.
 *
 * <p>Hace falta ademas porque las APIs publicas de codigos postales devuelven la
 * <b>comunidad autonoma</b> en el campo que llaman «state»: para el 15001 dicen
 * «Galicia», no «A Coruna». En una factura eso esta mal, porque el domicilio
 * fiscal lleva provincia. Asi que la ciudad se pregunta fuera y la provincia
 * sale de aqui.
 *
 * <p>Los nombres van con la grafia oficial, incluidas las cooficiales que el
 * Estado reconoce como unica forma («A Coruna», «Girona», «Lleida», «Illes
 * Balears», «Araba/Alava»...), porque es la que tiene que salir impresa.
 */
public final class Provincias {

    private Provincias() {
    }

    private static final Map<String, String> POR_PREFIJO = Map.ofEntries(
            Map.entry("01", "Araba/Álava"),
            Map.entry("02", "Albacete"),
            Map.entry("03", "Alicante/Alacant"),
            Map.entry("04", "Almería"),
            Map.entry("05", "Ávila"),
            Map.entry("06", "Badajoz"),
            Map.entry("07", "Illes Balears"),
            Map.entry("08", "Barcelona"),
            Map.entry("09", "Burgos"),
            Map.entry("10", "Cáceres"),
            Map.entry("11", "Cádiz"),
            Map.entry("12", "Castellón/Castelló"),
            Map.entry("13", "Ciudad Real"),
            Map.entry("14", "Córdoba"),
            Map.entry("15", "A Coruña"),
            Map.entry("16", "Cuenca"),
            Map.entry("17", "Girona"),
            Map.entry("18", "Granada"),
            Map.entry("19", "Guadalajara"),
            Map.entry("20", "Gipuzkoa"),
            Map.entry("21", "Huelva"),
            Map.entry("22", "Huesca"),
            Map.entry("23", "Jaén"),
            Map.entry("24", "León"),
            Map.entry("25", "Lleida"),
            Map.entry("26", "La Rioja"),
            Map.entry("27", "Lugo"),
            Map.entry("28", "Madrid"),
            Map.entry("29", "Málaga"),
            Map.entry("30", "Murcia"),
            Map.entry("31", "Navarra"),
            Map.entry("32", "Ourense"),
            Map.entry("33", "Asturias"),
            Map.entry("34", "Palencia"),
            Map.entry("35", "Las Palmas"),
            Map.entry("36", "Pontevedra"),
            Map.entry("37", "Salamanca"),
            Map.entry("38", "Santa Cruz de Tenerife"),
            Map.entry("39", "Cantabria"),
            Map.entry("40", "Segovia"),
            Map.entry("41", "Sevilla"),
            Map.entry("42", "Soria"),
            Map.entry("43", "Tarragona"),
            Map.entry("44", "Teruel"),
            Map.entry("45", "Toledo"),
            Map.entry("46", "Valencia/València"),
            Map.entry("47", "Valladolid"),
            Map.entry("48", "Bizkaia"),
            Map.entry("49", "Zamora"),
            Map.entry("50", "Zaragoza"),
            Map.entry("51", "Ceuta"),
            Map.entry("52", "Melilla"));

    /**
     * La provincia de un codigo postal, completo o a medio escribir.
     *
     * <p>Le basta con los dos primeros digitos, que es lo que permite rellenar el
     * campo mientras el usuario sigue tecleando. Devuelve vacio si todavia no hay
     * dos digitos o si el prefijo no es una provincia (00, 53 en adelante).
     */
    public static Optional<String> de(String codigoPostal) {
        if (codigoPostal == null) {
            return Optional.empty();
        }
        String digitos = codigoPostal.replaceAll("\\D", "");
        if (digitos.length() < 2) {
            return Optional.empty();
        }
        return Optional.ofNullable(POR_PREFIJO.get(digitos.substring(0, 2)));
    }

    /** Un codigo postal espanol completo: cinco digitos de una provincia real. */
    public static boolean esCompleto(String codigoPostal) {
        if (codigoPostal == null) {
            return false;
        }
        String digitos = codigoPostal.replaceAll("\\D", "");
        return digitos.length() == 5 && POR_PREFIJO.containsKey(digitos.substring(0, 2));
    }

    /** Los cinco digitos, sin espacios ni guiones. Vacio si no llega a cinco. */
    public static Optional<String> normalizar(String codigoPostal) {
        if (codigoPostal == null) {
            return Optional.empty();
        }
        String digitos = codigoPostal.replaceAll("\\D", "");
        return digitos.length() == 5 ? Optional.of(digitos) : Optional.empty();
    }
}
