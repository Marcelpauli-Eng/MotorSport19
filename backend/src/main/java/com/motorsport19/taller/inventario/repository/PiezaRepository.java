package com.motorsport19.taller.inventario.repository;

import com.motorsport19.taller.inventario.domain.Pieza;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PiezaRepository extends JpaRepository<Pieza, Long> {

    @Query("SELECT p FROM Pieza p WHERE UPPER(p.sku) = UPPER(:sku)")
    Optional<Pieza> buscarPorSku(@Param("sku") String sku);

    @Query("SELECT COUNT(p) > 0 FROM Pieza p WHERE UPPER(p.sku) = UPPER(:sku)")
    boolean existeConSku(@Param("sku") String sku);

    @Query("SELECT COUNT(p) > 0 FROM Pieza p WHERE UPPER(p.sku) = UPPER(:sku) AND p.id <> :idExcluido")
    boolean existeOtraConSku(@Param("sku") String sku, @Param("idExcluido") Long idExcluido);

    /**
     * Carga la pieza bloqueando su fila.
     *
     * <p>Se usa antes de comprobar existencias: sin el bloqueo, dos consumos
     * simultaneos de la ultima unidad podrian ver ambos "queda 1" y pasar los dos
     * la comprobacion. El trigger de la base de datos tambien bloquea, asi que el
     * stock nunca quedaria negativo, pero tomando el bloqueo aqui el usuario
     * recibe un mensaje de negocio claro en vez de un error de base de datos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pieza p WHERE p.id = :id")
    Optional<Pieza> buscarParaActualizarStock(@Param("id") Long id);

    /** Piezas que han caido al minimo o por debajo: alimenta las alertas de reposicion. */
    @Query("""
            SELECT p FROM Pieza p
              LEFT JOIN FETCH p.proveedor
             WHERE p.activo = TRUE
               AND p.stockActual <= p.stockMinimo
             ORDER BY p.stockActual ASC, p.sku ASC
            """)
    List<Pieza> buscarBajoMinimo();

    // El proveedor se trae con LEFT JOIN FETCH porque la respuesta muestra su
    // nombre y la sesion ya esta cerrada al serializar (open-in-view desactivado).
    // Los :texto van con CAST explicito. Sin el, cuando la busqueda llega vacia
    // PostgreSQL recibe un parametro sin tipo y falla con
    // «function upper(bytea) does not exist»: el listado sin filtro, que es la
    // vista por defecto, respondia 500.
    @Query(value = """
            SELECT p FROM Pieza p
              LEFT JOIN FETCH p.proveedor
             WHERE (:soloActivas = FALSE OR p.activo = TRUE)
               AND (:soloBajoMinimo = FALSE OR p.stockActual <= p.stockMinimo)
               AND (:proveedorId IS NULL OR p.proveedor.id = :proveedorId)
               AND (CAST(:familia AS String) IS NULL OR p.familia = CAST(:familia AS String))
               AND (CAST(:texto AS String) IS NULL
                    OR UPPER(p.sku)         LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%'))
                    OR UPPER(p.descripcion) LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%'))
                    OR UPPER(p.marca)       LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%')))
            """,
            countQuery = """
            SELECT COUNT(p) FROM Pieza p
             WHERE (:soloActivas = FALSE OR p.activo = TRUE)
               AND (:soloBajoMinimo = FALSE OR p.stockActual <= p.stockMinimo)
               AND (:proveedorId IS NULL OR p.proveedor.id = :proveedorId)
               AND (CAST(:familia AS String) IS NULL OR p.familia = CAST(:familia AS String))
               AND (CAST(:texto AS String) IS NULL
                    OR UPPER(p.sku)         LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%'))
                    OR UPPER(p.descripcion) LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%'))
                    OR UPPER(p.marca)       LIKE UPPER(CONCAT('%', CAST(:texto AS String), '%')))
            """)
    Page<Pieza> buscar(@Param("texto") String texto,
                       @Param("familia") String familia,
                       @Param("proveedorId") Long proveedorId,
                       @Param("soloActivas") boolean soloActivas,
                       @Param("soloBajoMinimo") boolean soloBajoMinimo,
                       Pageable pageable);

    /**
     * Familias que ya se usan, para el desplegable.
     *
     * <p>Se sacan de las propias piezas y no de un catalogo aparte: cada taller
     * agrupa a su manera, y asi la lista se construye sola segun se dan de alta.
     */
    @Query("SELECT DISTINCT p.familia FROM Pieza p WHERE p.familia IS NOT NULL AND p.activo = TRUE ORDER BY p.familia")
    List<String> familias();

    /** Carga la pieza con su proveedor resuelto, para devolverla en la respuesta. */
    @Query("SELECT p FROM Pieza p LEFT JOIN FETCH p.proveedor WHERE p.id = :id")
    Optional<Pieza> buscarConProveedor(@Param("id") Long id);

    @Query("SELECT p FROM Pieza p LEFT JOIN FETCH p.proveedor WHERE UPPER(p.sku) = UPPER(:sku)")
    Optional<Pieza> buscarPorSkuConProveedor(@Param("sku") String sku);
}
