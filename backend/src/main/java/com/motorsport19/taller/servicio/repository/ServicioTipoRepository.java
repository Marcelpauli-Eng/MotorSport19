package com.motorsport19.taller.servicio.repository;

import com.motorsport19.taller.servicio.domain.ServicioTipo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ServicioTipoRepository extends JpaRepository<ServicioTipo, Long> {

    /**
     * Listado completo.
     *
     * <p>Trae las lineas de una vez: la lista enseña las horas y el numero de
     * piezas de cada plantilla, y sin el grafo eso serian dos consultas por
     * fila. Son pocas plantillas —un taller tiene diez o quince—, asi que
     * cargarlas enteras sale mas barato que paginar.
     */
    @EntityGraph(attributePaths = {"lineas", "lineas.pieza"})
    @Query("SELECT s FROM ServicioTipo s ORDER BY LOWER(s.nombre) ASC")
    List<ServicioTipo> listarTodos();

    @EntityGraph(attributePaths = {"lineas", "lineas.pieza"})
    @Query("SELECT s FROM ServicioTipo s WHERE s.activo = TRUE ORDER BY LOWER(s.nombre) ASC")
    List<ServicioTipo> listarActivos();

    @EntityGraph(attributePaths = {"lineas", "lineas.pieza"})
    @Query("SELECT s FROM ServicioTipo s WHERE s.id = :id")
    Optional<ServicioTipo> buscarConLineas(Long id);

    /**
     * Comprueba el nombre antes de intentar guardar.
     *
     * <p>El indice unico de la base de datos es quien manda, pero si se deja
     * saltar a el, el usuario recibe un error de restriccion en vez de «ya
     * tienes un servicio que se llama asi».
     */
    @Query("""
            SELECT COUNT(s) > 0 FROM ServicioTipo s
             WHERE LOWER(TRIM(s.nombre)) = LOWER(TRIM(:nombre))
               AND (:idExcluido IS NULL OR s.id <> :idExcluido)
            """)
    boolean existeNombre(String nombre, Long idExcluido);
}
