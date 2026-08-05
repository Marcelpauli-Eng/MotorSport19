package com.motorsport19.taller.cliente.repository;

import com.motorsport19.taller.cliente.domain.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * Busqueda de mostrador: el mismo cuadro de texto sirve para nombre,
     * apellidos, documento, telefono o email.
     */
    @Query("""
            SELECT c FROM Cliente c
             WHERE (:soloActivos = FALSE OR c.activo = TRUE)
               AND (:texto IS NULL
                    OR UPPER(c.nombre)    LIKE UPPER(CONCAT('%', :texto, '%'))
                    OR UPPER(c.apellidos) LIKE UPPER(CONCAT('%', :texto, '%'))
                    OR UPPER(c.documento) LIKE UPPER(CONCAT('%', :texto, '%'))
                    OR c.telefono         LIKE CONCAT('%', :texto, '%')
                    OR UPPER(c.email)     LIKE UPPER(CONCAT('%', :texto, '%')))
            """)
    Page<Cliente> buscar(@Param("texto") String texto,
                         @Param("soloActivos") boolean soloActivos,
                         Pageable pageable);
}
