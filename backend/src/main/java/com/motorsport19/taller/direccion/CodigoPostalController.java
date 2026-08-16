package com.motorsport19.taller.direccion;

import com.motorsport19.taller.direccion.CodigoPostalService.DatosCodigoPostal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ayuda para rellenar una direccion a partir del codigo postal.
 *
 * <p>Se llama mientras el usuario teclea, no al terminar. Por eso acepta el
 * codigo a medias: con dos digitos ya contesta la provincia, y el campo se
 * rellena antes de que acabe de escribir. Con los cinco, ademas intenta la
 * ciudad.
 *
 * <p>Siempre responde 200, incluso con un codigo que no existe o si el servicio
 * exterior no contesta: en ese caso vienen los campos vacios. Un fallo aqui no
 * es un fallo del alta —el usuario escribe la ciudad a mano y sigue—, y
 * contestar con un error obligaria a la pantalla a distinguir entre «no lo se»
 * y «algo va mal» a cada tecla.
 *
 * <p>No lleva permiso propio: es informacion publica de Correos, no dice nada
 * del taller ni de sus clientes. Basta con haber entrado.
 */
@RestController
@RequestMapping("/codigos-postales")
public class CodigoPostalController {

    private final CodigoPostalService servicio;

    public CodigoPostalController(CodigoPostalService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{codigoPostal}")
    public CodigoPostalResponse consultar(@PathVariable String codigoPostal) {
        DatosCodigoPostal datos = servicio.resolver(codigoPostal);
        return new CodigoPostalResponse(
                datos.codigoPostal(), datos.provincia(), datos.ciudad(), datos.ciudades());
    }

    /**
     * @param provincia la de los dos primeros digitos, o nulo si aun no hay dos
     * @param ciudad    la ciudad cuando no hay duda; nulo si hay varias o ninguna
     * @param ciudades  todas las posibles, para que la pantalla ofrezca elegir
     */
    public record CodigoPostalResponse(
            String codigoPostal,
            String provincia,
            String ciudad,
            List<String> ciudades) {
    }
}
