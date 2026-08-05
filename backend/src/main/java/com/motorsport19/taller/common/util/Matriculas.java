package com.motorsport19.taller.common.util;

/**
 * Normalizacion de matriculas.
 *
 * <p>En mostrador la misma matricula se teclea de mil formas ("1234ABC",
 * "1234-abc", "1234 ABC"). El indice unico de la base de datos compara en
 * mayusculas, asi que aqui se deja siempre una forma canonica y se evita crear
 * dos fichas para la misma moto.
 */
public final class Matriculas {

    private Matriculas() {
    }

    /**
     * Deja la matricula en mayusculas, sin separadores y con un unico espacio
     * entre numeros y letras cuando tiene el formato espanol actual.
     *
     * @return la matricula normalizada, o {@code null} si la entrada era nula o vacia
     */
    public static String normalizar(String matricula) {
        if (matricula == null) {
            return null;
        }
        String limpia = matricula.replaceAll("[\\s.\\-]", "").toUpperCase();
        if (limpia.isEmpty()) {
            return null;
        }
        // Formato espanol desde 2000: cuatro digitos y tres consonantes.
        if (limpia.matches("\\d{4}[A-Z]{3}")) {
            return limpia.substring(0, 4) + " " + limpia.substring(4);
        }
        return limpia;
    }
}
