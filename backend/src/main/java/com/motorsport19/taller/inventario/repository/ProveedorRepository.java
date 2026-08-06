package com.motorsport19.taller.inventario.repository;

import com.motorsport19.taller.inventario.domain.Proveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    @Query("SELECT COUNT(p) > 0 FROM Proveedor p WHERE UPPER(p.nif) = UPPER(:nif)")
    boolean existeConNif(@Param("nif") String nif);

    @Query("SELECT COUNT(p) > 0 FROM Proveedor p WHERE UPPER(p.nif) = UPPER(:nif) AND p.id <> :idExcluido")
    boolean existeOtroConNif(@Param("nif") String nif, @Param("idExcluido") Long idExcluido);

    // Los :texto van con CAST explicito. Sin el, cuando la busqueda llega vacia
    // PostgreSQL recibe un parametro sin tipo y falla con
    // «function upper(bytea) does not exist»: el listado sin filtro, que es la
    // vista por defecto, respondia 500.
    @Query("""
            SELECT p FROM Proveedor p
             WHERE (:soloActivos = FALSE OR p.activo = TRUE)
               AND (CAST(:texto AS String) IS NULL
                    OR UPPER(p.nombre) LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%'))
                    OR UPPER(p.nif)    LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%')))
            """)
    Page<Proveedor> buscar(@Param("texto") String texto,
                           @Param("soloActivos") boolean soloActivos,
                           Pageable pageable);
}
