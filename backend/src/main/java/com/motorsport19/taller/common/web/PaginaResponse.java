package com.motorsport19.taller.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Pagina de resultados con la forma que consume el frontend.
 *
 * <p>Se usa en lugar de serializar directamente {@code Page} de Spring Data,
 * cuyo JSON incluye detalles internos de paginacion que ni el frontend necesita
 * ni conviene fijar como contrato publico de la API.
 *
 * @param contenido    elementos de esta pagina
 * @param pagina       numero de pagina, empezando en 0
 * @param tamano       tamano de pagina solicitado
 * @param totalItems   total de elementos que cumplen el filtro
 * @param totalPaginas total de paginas
 * @param primera      indica si es la primera pagina
 * @param ultima       indica si es la ultima pagina
 */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalItems,
        int totalPaginas,
        boolean primera,
        boolean ultima
) {

    /** Convierte una pagina de entidades en una pagina de DTOs. */
    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> conversor) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(conversor).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.isFirst(),
                pagina.isLast());
    }
}
