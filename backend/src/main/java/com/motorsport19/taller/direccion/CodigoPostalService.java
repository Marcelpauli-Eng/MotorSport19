package com.motorsport19.taller.direccion;

import com.motorsport19.taller.common.util.Provincias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Que provincia y que ciudad corresponden a un codigo postal.
 *
 * <p>Son dos preguntas distintas y se resuelven de dos maneras distintas a
 * proposito:
 *
 * <ul>
 *   <li><b>La provincia</b> sale de los dos primeros digitos, aqui mismo. No
 *       necesita red, no falla nunca y contesta cuando el usuario todavia esta
 *       tecleando el codigo.</li>
 *   <li><b>La ciudad</b> hay que preguntarla fuera, porque son casi doce mil
 *       codigos postales y ese callejero no esta en el programa.</li>
 * </ul>
 *
 * <p>Que la ciudad dependa de internet es una decision tomada: si el taller se
 * queda sin linea, el formulario deja de sugerir y se escribe a mano, que es
 * exactamente lo que se hacia antes. Lo que <b>no</b> puede pasar es que la
 * pantalla se quede colgada esperando; de ahi los tiempos de espera cortos y
 * que cualquier fallo se trague en silencio y devuelva solo la provincia.
 *
 * <p>Lo consultado se guarda en memoria. Un taller repite los mismos veinte o
 * treinta codigos postales de su zona un dia tras otro, asi que a partir de la
 * segunda vez la respuesta es inmediata y no se molesta a un servicio ajeno y
 * gratuito con la misma pregunta cien veces.
 */
@Service
public class CodigoPostalService {

    private static final Logger log = LoggerFactory.getLogger(CodigoPostalService.class);

    private static final Locale ESPANOL = Locale.forLanguageTag("es");

    /**
     * Palabras que en un toponimo van en minuscula salvo al principio.
     *
     * <p>La fuente devuelve todo con inicial mayuscula («Palma De Mallorca»,
     * «El Prat De Llobregat»), que no es como se escribe ni como tiene que salir
     * impreso en una factura.
     */
    private static final Set<String> ENLACES = Set.of(
            "de", "del", "la", "las", "los", "el", "y", "e", "i", "a", "en", "o", "da", "do", "dos");

    private final RestClient cliente;
    private final boolean activo;

    /** Codigo postal completo -> ciudades. Se llena solo, segun se pregunta. */
    private final Map<String, List<String>> memoria = new ConcurrentHashMap<>();

    public CodigoPostalService(
            @Value("${motorsport19.codigos-postales.url:https://api.zippopotam.us/ES/}") String url,
            @Value("${motorsport19.codigos-postales.activo:true}") boolean activo,
            @Value("${motorsport19.codigos-postales.espera-ms:2500}") int esperaMs) {

        this.activo = activo;

        // Tiempos de espera cortos y explicitos. Sin esto, el cliente HTTP de
        // Java espera indefinidamente: una caida del servicio ajeno dejaria el
        // formulario del taller esperando a alguien que no va a contestar.
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(Math.min(esperaMs, 1500)));
        fabrica.setReadTimeout(Duration.ofMillis(esperaMs));

        this.cliente = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .build();
    }

    /**
     * Lo que se sabe de un codigo postal, este completo o a medio escribir.
     *
     * <p>Nunca lanza: si la consulta exterior falla, devuelve la provincia y
     * ninguna ciudad. Para quien rellena el formulario eso es «te ayudo con lo
     * que puedo», no un error.
     */
    public DatosCodigoPostal resolver(String codigoPostal) {
        String provincia = Provincias.de(codigoPostal).orElse(null);

        // Con menos de cinco digitos no hay nada que preguntar: la ciudad aun no
        // esta determinada y la provincia ya va en la respuesta.
        Optional<String> completo = Provincias.normalizar(codigoPostal);
        if (provincia == null || completo.isEmpty()) {
            return new DatosCodigoPostal(codigoPostal, provincia, List.of());
        }

        return new DatosCodigoPostal(completo.get(), provincia, ciudadesDe(completo.get()));
    }

    private List<String> ciudadesDe(String codigoPostal) {
        List<String> recordadas = memoria.get(codigoPostal);
        if (recordadas != null) {
            return recordadas;
        }
        if (!activo) {
            return List.of();
        }

        List<String> ciudades = preguntarFuera(codigoPostal);
        // Se recuerda incluso la lista vacia: un codigo postal que no existe no
        // va a empezar a existir, y asi no se pregunta dos veces por el.
        memoria.put(codigoPostal, ciudades);
        return ciudades;
    }

    private List<String> preguntarFuera(String codigoPostal) {
        try {
            Respuesta respuesta = cliente.get()
                    .uri(codigoPostal)
                    .retrieve()
                    .body(Respuesta.class);

            if (respuesta == null || respuesta.places() == null) {
                return List.of();
            }
            return respuesta.places().stream()
                    .map(Lugar::nombre)
                    .filter(n -> n != null && !n.isBlank())
                    .map(CodigoPostalService::conMayusculasDeToponimo)
                    .distinct()
                    .toList();

        } catch (Exception e) {
            // Sin red, servicio caido, respuesta rara o codigo que no existe. En
            // ningun caso es un problema del taller: se sigue sin la ciudad.
            log.debug("No se ha podido consultar el codigo postal {}: {}", codigoPostal, e.toString());
            return List.of();
        }
    }

    /**
     * «Palma De Mallorca» -> «Palma de Mallorca».
     *
     * <p>La primera palabra siempre en mayuscula: «El Prat de Llobregat» y
     * «Las Palmas de Gran Canaria» empiezan por articulo y asi se escriben.
     */
    static String conMayusculasDeToponimo(String nombre) {
        String[] palabras = nombre.trim().split("\\s+");
        StringBuilder texto = new StringBuilder(nombre.length());
        for (int i = 0; i < palabras.length; i++) {
            String minuscula = palabras[i].toLowerCase(ESPANOL);
            if (i > 0) {
                texto.append(' ');
            }
            texto.append(i > 0 && ENLACES.contains(minuscula) ? minuscula : palabras[i]);
        }
        return texto.toString();
    }

    /** Lo que se sabe de un codigo postal. */
    public record DatosCodigoPostal(String codigoPostal, String provincia, List<String> ciudades) {

        /** La ciudad, si no hay duda. Con varias, decide quien rellena el formulario. */
        public String ciudad() {
            return ciudades.size() == 1 ? ciudades.get(0) : null;
        }
    }

    // Forma de la respuesta del servicio exterior. Los nombres son los suyos.
    private record Respuesta(List<Lugar> places) {
    }

    private record Lugar(@JsonProperty("place name") String nombre) {
    }
}
