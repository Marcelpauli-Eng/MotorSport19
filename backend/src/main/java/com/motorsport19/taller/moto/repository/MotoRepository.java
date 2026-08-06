package com.motorsport19.taller.moto.repository;

import com.motorsport19.taller.moto.domain.Moto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MotoRepository extends JpaRepository<Moto, Long> {

    // El cliente se trae con JOIN FETCH en las consultas de detalle porque la
    // respuesta muestra su nombre y la sesion ya esta cerrada al serializar
    // (open-in-view desactivado).
    @Query("SELECT m FROM Moto m JOIN FETCH m.cliente WHERE UPPER(m.matricula) = UPPER(:matricula)")
    Optional<Moto> buscarPorMatricula(@Param("matricula") String matricula);

    /** Carga la moto con su propietario resuelto. */
    @Query("SELECT m FROM Moto m JOIN FETCH m.cliente WHERE m.id = :id")
    Optional<Moto> buscarConCliente(@Param("id") Long id);

    @Query("SELECT COUNT(m) > 0 FROM Moto m WHERE UPPER(m.matricula) = UPPER(:matricula)")
    boolean existeConMatricula(@Param("matricula") String matricula);

    @Query("SELECT COUNT(m) > 0 FROM Moto m WHERE UPPER(m.matricula) = UPPER(:matricula) AND m.id <> :idExcluido")
    boolean existeOtraConMatricula(@Param("matricula") String matricula, @Param("idExcluido") Long idExcluido);

    @Query("SELECT COUNT(m) > 0 FROM Moto m WHERE UPPER(m.numeroBastidor) = UPPER(:bastidor) AND m.id <> :idExcluido")
    boolean existeOtraConBastidor(@Param("bastidor") String bastidor, @Param("idExcluido") Long idExcluido);

    /** Motos de un cliente. Se usa al abrir una OT desde la ficha del cliente. */
    @Query("SELECT m FROM Moto m WHERE m.cliente.id = :clienteId AND (:soloActivas = FALSE OR m.activo = TRUE) ORDER BY m.matricula")
    List<Moto> buscarPorCliente(@Param("clienteId") Long clienteId, @Param("soloActivas") boolean soloActivas);

    // Los :texto van con CAST explicito. Sin el, cuando la busqueda llega vacia
    // PostgreSQL recibe un parametro sin tipo y falla con
    // «function upper(bytea) does not exist»: el listado sin filtro, que es la
    // vista por defecto, respondia 500.
    @Query("""
            SELECT m FROM Moto m
             WHERE (:soloActivas = FALSE OR m.activo = TRUE)
               AND (CAST(:texto AS String) IS NULL
                    OR UPPER(m.matricula)      LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%'))
                    OR UPPER(m.marca)          LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%'))
                    OR UPPER(m.modelo)         LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%'))
                    OR UPPER(m.numeroBastidor) LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%')))
            """)
    Page<Moto> buscar(@Param("texto") String texto,
                      @Param("soloActivas") boolean soloActivas,
                      Pageable pageable);
}
