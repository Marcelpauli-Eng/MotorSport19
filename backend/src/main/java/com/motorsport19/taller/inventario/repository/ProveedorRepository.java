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

    @Query("""
            SELECT p FROM Proveedor p
             WHERE (:soloActivos = FALSE OR p.activo = TRUE)
               AND (:texto IS NULL
                    OR UPPER(p.nombre) LIKE UPPER(CONCAT('%', :texto, '%'))
                    OR UPPER(p.nif)    LIKE UPPER(CONCAT('%', :texto, '%')))
            """)
    Page<Proveedor> buscar(@Param("texto") String texto,
                           @Param("soloActivos") boolean soloActivos,
                           Pageable pageable);
}
