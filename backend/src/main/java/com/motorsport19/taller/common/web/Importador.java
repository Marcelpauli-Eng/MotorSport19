package com.motorsport19.taller.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ManejadorGlobalErrores;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Altas en bloque desde un fichero (CSV o JSON).
 *
 * <p>El navegador lee el fichero y manda las filas ya con los nombres de campo
 * del alta normal. Aqui cada fila pasa por lo mismo que un alta a mano —las
 * validaciones del formulario y las reglas del servicio— y va en su propia
 * transaccion: una fila mala no tumba a las demas, se apunta con su motivo y
 * se sigue con la siguiente.
 *
 * <p>Por eso no hay que llamarlo desde dentro de una transaccion: con una sola
 * para todo, el primer error la dejaria marcada para deshacer y se perderian
 * tambien las filas buenas.
 */
@Component
public class Importador {

    private static final Logger log = LoggerFactory.getLogger(Importador.class);

    private final ObjectMapper json;
    private final Validator validador;

    public Importador(ObjectMapper json, Validator validador) {
        this.json = json;
        this.validador = validador;
    }

    /** Lo que hay que saber de una fila. {@code fila} cuenta desde 1, en el orden en que llegaron. */
    public record Nota(int fila, String motivo) {
    }

    /**
     * @param rechazadas las que no han entrado
     * @param avisos     las que han entrado, pero dejando algo que revisar en su ficha
     */
    public record Resultado(int creadas, List<Nota> rechazadas, List<Nota> avisos) {
    }

    /**
     * @param alta da de alta una fila; apunta en la lista lo que haya tenido que
     *             dejar a un lado para que entrase
     */
    public Resultado importar(List<Map<String, Object>> filas,
                             BiConsumer<Map<String, Object>, List<String>> alta) {
        List<Nota> rechazadas = new ArrayList<>();
        List<Nota> avisos = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            List<String> apartado = new ArrayList<>();
            try {
                alta.accept(filas.get(i), apartado);
            } catch (RuntimeException e) {
                rechazadas.add(new Nota(i + 1, motivo(e)));
                continue;
            }
            if (!apartado.isEmpty()) {
                avisos.add(new Nota(i + 1, String.join(" ", apartado)));
            }
        }
        return new Resultado(filas.size() - rechazadas.size(), rechazadas, avisos);
    }

    /**
     * Un dato opcional mal escrito no deja fuera la fila entera: se quita, se
     * guarda tal cual en observaciones y se avisa, para corregirlo en la ficha.
     * Es lo normal al traer datos de otro programa, que no los comprobaba.
     *
     * @param nombre como se le llama en el aviso: «El email»
     */
    public void apartarSiNoVale(Map<String, Object> fila, Class<?> tipo, String campo, String nombre,
                                List<String> avisos) {
        Object valor = fila.get(campo);
        if (valor == null || validador.validateValue(tipo, campo, valor).isEmpty()) {
            return;
        }
        fila.remove(campo);
        anotar(fila, "%s del fichero importado, que no es valido: %s".formatted(nombre, valor));
        avisos.add("%s '%s' no es valido: ha entrado sin el y queda en observaciones.".formatted(nombre, valor));
    }

    /** Añade una linea a las observaciones de la fila, detras de las que ya traiga. */
    public static void anotar(Map<String, Object> fila, String nota) {
        fila.merge("observaciones", nota, (antes, nueva) -> antes + "\n" + nueva);
    }

    /**
     * Convierte la fila en la peticion del alta normal y le pasa sus mismas
     * validaciones, que Spring solo aplica solas al cuerpo de una peticion.
     */
    public <T> T leer(Map<String, Object> fila, Class<T> tipo) {
        T peticion;
        try {
            peticion = json.convertValue(fila, tipo);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(e.getCause() instanceof InvalidFormatException formato
                    ? ManejadorGlobalErrores.mensajeDeFormato(formato)
                    : "Hay un dato en esta fila que no se puede leer.");
        }
        String fallos = validador.validate(peticion).stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining(". "));
        if (!fallos.isEmpty()) {
            throw new ReglaNegocioException(fallos + ".");
        }
        return peticion;
    }

    /** Las reglas del programa ya explican el motivo en espanol; lo demas es un fallo. */
    private static String motivo(RuntimeException e) {
        if (e instanceof ReglaNegocioException || e instanceof ConflictoException
                || e instanceof RecursoNoEncontradoException) {
            return e.getMessage();
        }
        log.warn("Fila importada que ha fallado de forma inesperada", e);
        return "No se ha podido guardar: " + NestedExceptionUtils.getMostSpecificCause(e).getMessage();
    }
}
