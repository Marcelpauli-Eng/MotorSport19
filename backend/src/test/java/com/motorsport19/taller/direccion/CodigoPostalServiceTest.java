package com.motorsport19.taller.direccion;

import com.motorsport19.taller.direccion.CodigoPostalService.DatosCodigoPostal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La ayuda para rellenar la direccion desde el codigo postal.
 *
 * <p>Lo que mas importa de este servicio no es que acierte la ciudad, sino que
 * <b>nunca estorbe</b>: el alta de un cliente no puede depender de que un
 * servicio ajeno y gratuito este disponible. Aqui se comprueba justamente eso,
 * apagando la consulta exterior.
 */
@DisplayName("Codigo postal")
class CodigoPostalServiceTest {

    /** El servicio con la consulta exterior apagada: es el taller sin internet. */
    private final CodigoPostalService sinRed =
            new CodigoPostalService("http://no.se.usa/", false, 2500);

    @Nested
    @DisplayName("Sin internet")
    class SinInternet {

        @Test
        @DisplayName("la provincia se rellena igual, que es lo que se prometio")
        void provinciaSiempre() {
            DatosCodigoPostal datos = sinRed.resolver("08820");

            assertThat(datos.provincia()).isEqualTo("Barcelona");
            assertThat(datos.codigoPostal()).isEqualTo("08820");
        }

        @Test
        @DisplayName("la ciudad se queda vacia, sin error y sin espera")
        void ciudadVacia() {
            DatosCodigoPostal datos = sinRed.resolver("08820");

            assertThat(datos.ciudades()).isEmpty();
            assertThat(datos.ciudad()).isNull();
        }

        @Test
        @DisplayName("con dos digitos ya contesta, sin llegar a preguntar nada")
        void aMedioTeclear() {
            assertThat(sinRed.resolver("08").provincia()).isEqualTo("Barcelona");
            assertThat(sinRed.resolver("088").provincia()).isEqualTo("Barcelona");
        }

        @Test
        @DisplayName("un codigo que no existe no da error: devuelve los campos vacios")
        void codigoInexistente() {
            DatosCodigoPostal datos = sinRed.resolver("99999");

            assertThat(datos.provincia()).isNull();
            assertThat(datos.ciudades()).isEmpty();
        }

        @Test
        @DisplayName("nulo y vacio tampoco revientan")
        void sinCodigo() {
            assertThat(sinRed.resolver(null).provincia()).isNull();
            assertThat(sinRed.resolver("").provincia()).isNull();
            assertThat(sinRed.resolver("   ").ciudades()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Con el servicio exterior caido")
    class ServicioCaido {

        /** Apunta a una direccion que no responde: es una caida de verdad. */
        private final CodigoPostalService roto =
                new CodigoPostalService("http://localhost:1/", true, 300);

        @Test
        @DisplayName("se traga el fallo y devuelve la provincia igualmente")
        void noPropaga() {
            DatosCodigoPostal datos = roto.resolver("28001");

            assertThat(datos.provincia()).isEqualTo("Madrid");
            assertThat(datos.ciudades()).isEmpty();
        }

        @Test
        @DisplayName("no vuelve a preguntar por el mismo codigo")
        void recuerdaElFallo() {
            roto.resolver("28001");
            long antes = System.nanoTime();
            roto.resolver("28001");
            long tardaMs = (System.nanoTime() - antes) / 1_000_000;

            // La segunda sale de memoria: si volviera a intentar la conexion,
            // tardaria al menos lo que tarde en darse por vencida.
            assertThat(tardaMs).isLessThan(100);
        }
    }

    @Nested
    @DisplayName("Nombres de poblacion")
    class Toponimos {

        @ParameterizedTest(name = "«{0}» -> «{1}»")
        @CsvSource({
                "'Palma De Mallorca',          'Palma de Mallorca'",
                "'El Prat De Llobregat',       'El Prat de Llobregat'",
                "'Las Palmas De Gran Canaria', 'Las Palmas de Gran Canaria'",
                "'San Sebastian De Los Reyes', 'San Sebastian de los Reyes'",
                "'Santa Cruz De Tenerife',     'Santa Cruz de Tenerife'",
                "'Madrid',                     'Madrid'",
                "'A Coruña',                   'A Coruña'",
        })
        @DisplayName("las particulas van en minuscula, salvo al principio")
        void mayusculas(String comoLoManda, String comoDebeSalir) {
            assertThat(CodigoPostalService.conMayusculasDeToponimo(comoLoManda))
                    .isEqualTo(comoDebeSalir);
        }

        @Test
        @DisplayName("se corrigen las mayusculas, no la ortografia de la fuente")
        void noInventaTildes() {
            // Si el servicio manda «Alcala» sin tilde, sale «Alcala». Ponersela
            // seria adivinar, y en una direccion fiscal no se adivina.
            assertThat(CodigoPostalService.conMayusculasDeToponimo("Alcala De Henares"))
                    .isEqualTo("Alcala de Henares");
        }

        @Test
        @DisplayName("el articulo inicial se respeta")
        void articuloInicial() {
            assertThat(CodigoPostalService.conMayusculasDeToponimo("El Prat De Llobregat"))
                    .startsWith("El Prat")
                    .isEqualTo("El Prat de Llobregat");
            assertThat(CodigoPostalService.conMayusculasDeToponimo("Las Palmas"))
                    .isEqualTo("Las Palmas");
        }
    }

    @Nested
    @DisplayName("Cuando hay mas de una poblacion")
    class VariasPoblaciones {

        @Test
        @DisplayName("con una sola, se rellena sola")
        void unaSola() {
            var datos = new DatosCodigoPostal("28001", "Madrid", java.util.List.of("Madrid"));

            assertThat(datos.ciudad()).isEqualTo("Madrid");
        }

        @Test
        @DisplayName("con varias no se elige por el usuario: se le ofrecen")
        void varias() {
            var datos = new DatosCodigoPostal("08820", "Barcelona",
                    java.util.List.of("El Prat de Llobregat", "El Aeroport del Prat"));

            assertThat(datos.ciudad())
                    .as("elegir una al azar seria escribir en la factura algo que nadie ha dicho")
                    .isNull();
            assertThat(datos.ciudades()).hasSize(2);
        }

        @Test
        @DisplayName("con ninguna, tampoco")
        void ninguna() {
            var datos = new DatosCodigoPostal("08820", "Barcelona", java.util.List.of());

            assertThat(datos.ciudad()).isNull();
        }
    }
}
