package com.motorsport19.taller.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorsport19.taller.cliente.web.dto.CrearClienteRequest;
import com.motorsport19.taller.common.error.ConflictoException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importar un fichero es lo mismo que dar de alta fila a fila, sin que una
 * fila mala se lleve por delante a las buenas.
 */
@DisplayName("Importacion de ficheros")
class ImportadorTest {

    private final Importador importador = new Importador(new ObjectMapper(),
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    @DisplayName("una fila mala no tumba a las demas y cada una dice por que no ha entrado")
    void filaPorFila() {
        List<String> altas = new ArrayList<>();
        List<Map<String, Object>> filas = List.of(
                // El telefono llega como numero desde un JSON: vale igual.
                fila("nombre", "Rocio", "telefono", 600100107),
                fila("nombre", "Sin arroba", "email", "esto-no-es-un-email"),
                fila("nombre", "Con DNI", "tipoDocumento", "DNI", "documento", "12345678Z"),
                fila("nombre", "Repetido"),
                fila("nombre", "Paula"));

        Importador.Resultado resultado = importador.importar(filas, (f, avisos) -> {
            CrearClienteRequest p = importador.leer(f, CrearClienteRequest.class);
            if (p.nombre().equals("Repetido")) {
                throw new ConflictoException("Ya existe un cliente con el mismo nombre y contacto: Repetido.");
            }
            altas.add(p.nombre() + " " + p.telefono());
        });

        assertThat(altas).containsExactly("Rocio 600100107", "Paula null");
        assertThat(resultado.creadas()).isEqualTo(2);
        assertThat(resultado.rechazadas()).extracting(Importador.Nota::fila).containsExactly(2, 3, 4);
        assertThat(resultado.rechazadas()).extracting(Importador.Nota::motivo).containsExactly(
                "El email no tiene un formato valido.",
                "El valor 'DNI' no vale para 'tipoDocumento'. Los admitidos son: NIF, CIF, NIE, PASAPORTE, OTRO.",
                "Ya existe un cliente con el mismo nombre y contacto: Repetido.");
    }

    @Test
    @DisplayName("un email mal escrito no deja fuera al cliente: entra sin el, queda anotado y se avisa")
    void apartaElEmailQueNoVale() {
        List<Map<String, Object>> filas = List.of(
                fila("nombre", "Eric", "email", "correo-roto@", "observaciones", "Cliente de siempre"),
                fila("nombre", "Paula", "email", "paula@correo.example"));
        List<CrearClienteRequest> altas = new ArrayList<>();

        Importador.Resultado resultado = importador.importar(filas, (f, avisos) -> {
            importador.apartarSiNoVale(f, CrearClienteRequest.class, "email", "El email", avisos);
            altas.add(importador.leer(f, CrearClienteRequest.class));
        });

        assertThat(resultado.creadas()).isEqualTo(2);
        assertThat(resultado.rechazadas()).isEmpty();
        assertThat(resultado.avisos()).extracting(Importador.Nota::fila).containsExactly(1);
        assertThat(resultado.avisos().get(0).motivo()).contains("'correo-roto@'", "observaciones");
        assertThat(altas.get(0).email()).isNull();
        assertThat(altas.get(0).observaciones())
                .isEqualTo("Cliente de siempre\nEl email del fichero importado, que no es valido: correo-roto@");
        assertThat(altas.get(1).email()).isEqualTo("paula@correo.example");
    }

    private static Map<String, Object> fila(Object... claveValor) {
        Map<String, Object> fila = new HashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            fila.put((String) claveValor[i], claveValor[i + 1]);
        }
        return fila;
    }
}
