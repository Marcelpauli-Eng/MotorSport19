package com.motorsport19.taller.cliente.repository;

import com.motorsport19.taller.cliente.domain.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * Busca por documento fiscal ignorando mayusculas, igual que hace el indice
     * unico de la base de datos.
     */
    @Query("SELECT c FROM Cliente c WHERE UPPER(c.documento) = UPPER(:documento)")
    Optional<Cliente> buscarPorDocumento(@Param("documento") String documento);

    @Query("SELECT COUNT(c) > 0 FROM Cliente c WHERE UPPER(c.documento) = UPPER(:documento) AND c.id <> :idExcluido")
    boolean existeOtroConDocumento(@Param("documento") String documento, @Param("idExcluido") Long idExcluido);

    @Query("SELECT COUNT(c) > 0 FROM Cliente c WHERE UPPER(c.documento) = UPPER(:documento)")
    boolean existeConDocumento(@Param("documento") String documento);

    /**
     * Otro cliente con el mismo nombre y el mismo contacto. Es como reconoce la
     * importacion a quien ya esta cuando la fila no trae documento.
     */
    // CAST por lo mismo que en buscar(): un parametro nulo llega sin tipo.
    @Query("""
            SELECT COUNT(c) > 0 FROM Cliente c
             WHERE UPPER(c.nombre) = UPPER(:nombre)
               AND COALESCE(UPPER(c.apellidos), '') = COALESCE(UPPER(CAST(:apellidos AS String)), '')
               AND COALESCE(c.telefono, '')         = COALESCE(CAST(:telefono AS String), '')
               AND COALESCE(UPPER(c.email), '')     = COALESCE(UPPER(CAST(:email AS String)), '')
            """)
    boolean existeIgual(@Param("nombre") String nombre, @Param("apellidos") String apellidos,
                        @Param("telefono") String telefono, @Param("email") String email);

    /** Clientes cuyo nombre completo, tal y como sale en su ficha, es este. */
    @Query("""
            SELECT c.id FROM Cliente c
             WHERE UPPER(TRIM(CONCAT(c.nombre, ' ', COALESCE(c.apellidos, '')))) = UPPER(:nombreCompleto)
            """)
    List<Long> idsConNombreCompleto(@Param("nombreCompleto") String nombreCompleto);

    /**
     * Busqueda de mostrador: el mismo cuadro de texto sirve para nombre,
     * apellidos, documento, telefono o email.
     */
    // Los :texto van con CAST explicito. Sin el, cuando la busqueda llega vacia
    // PostgreSQL recibe un parametro sin tipo y falla con
    // «function upper(bytea) does not exist»: el listado sin filtro, que es la
    // vista por defecto, respondia 500.
    @Query("""
            SELECT c FROM Cliente c
             WHERE (:soloActivos = FALSE OR c.activo = TRUE)
               AND (CAST(:texto AS String) IS NULL
                    OR UPPER(c.nombre)    LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%'))
                    OR UPPER(c.apellidos) LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%'))
                    OR UPPER(c.documento) LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%'))
                    OR c.telefono         LIKE CONCAT('%', CAST(:texto AS String), '%')
                    OR UPPER(c.email)     LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%')))
            """)
    Page<Cliente> buscar(@Param("texto") String texto,
                         @Param("soloActivos") boolean soloActivos,
                         Pageable pageable);
}
