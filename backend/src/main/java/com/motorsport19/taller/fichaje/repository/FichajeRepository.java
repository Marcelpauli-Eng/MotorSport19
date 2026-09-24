package com.motorsport19.taller.fichaje.repository;

import com.motorsport19.taller.fichaje.domain.Fichaje;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FichajeRepository extends JpaRepository<Fichaje, Long> {

    /**
     * La jornada que alguien tiene abierta ahora mismo, si la tiene.
     *
     * <p>Se consulta en cada peticion de quien tiene que fichar, asi que va por
     * el indice unico parcial que ya garantiza que no puede haber dos.
     */
    @Query("SELECT f FROM Fichaje f JOIN FETCH f.usuario WHERE f.usuario.id = :usuarioId AND f.fin IS NULL")
    Optional<Fichaje> buscarAbiertaDe(@Param("usuarioId") Long usuarioId);

    /** Si hay jornada abierta, sin traerse la fila entera. Para el filtro. */
    @Query("SELECT COUNT(f) > 0 FROM Fichaje f WHERE f.usuario.id = :usuarioId AND f.fin IS NULL")
    boolean tieneJornadaAbierta(@Param("usuarioId") Long usuarioId);

    /** Todas las que quedaron abiertas, de cualquiera. El aviso del administrador. */
    @Query("""
            SELECT f FROM Fichaje f
              JOIN FETCH f.usuario
             WHERE f.fin IS NULL
             ORDER BY f.inicio
            """)
    List<Fichaje> abiertas();

    /**
     * Las jornadas de un periodo, de todos o de una persona.
     *
     * <p>El filtro va sobre {@code inicio} y no sobre la fecha corregida: quien
     * busca «la semana pasada» busca por el dia que se trabajo.
     */
    @Query(value = """
            SELECT f FROM Fichaje f
              JOIN FETCH f.usuario
             WHERE f.inicio >= :desde AND f.inicio < :hasta
               AND (:usuarioId IS NULL OR f.usuario.id = :usuarioId)
             ORDER BY f.inicio DESC
            """,
            countQuery = """
            SELECT COUNT(f) FROM Fichaje f
             WHERE f.inicio >= :desde AND f.inicio < :hasta
               AND (:usuarioId IS NULL OR f.usuario.id = :usuarioId)
            """)
    Page<Fichaje> buscar(@Param("desde") Instant desde,
                         @Param("hasta") Instant hasta,
                         @Param("usuarioId") Long usuarioId,
                         Pageable pageable);

    /** Lo mismo sin paginar, para sumar el total del periodo. */
    @Query("""
            SELECT f FROM Fichaje f
              JOIN FETCH f.usuario
             WHERE f.inicio >= :desde AND f.inicio < :hasta
               AND (:usuarioId IS NULL OR f.usuario.id = :usuarioId)
             ORDER BY f.usuario.nombreCompleto, f.inicio
            """)
    List<Fichaje> buscarTodas(@Param("desde") Instant desde,
                              @Param("hasta") Instant hasta,
                              @Param("usuarioId") Long usuarioId);
}
