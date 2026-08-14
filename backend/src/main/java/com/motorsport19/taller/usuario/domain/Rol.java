package com.motorsport19.taller.usuario.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Un rol del taller: un nombre y la lista de lo que puede hacer quien lo lleva.
 *
 * <p>Antes eran tres valores clavados en el codigo. Servia mientras el reparto
 * de tareas fuera ese, pero en cuanto un taller quiere «que Juan de de alta
 * clientes pero no toque el almacen» no habia donde tocar. Ahora el
 * administrador los compone a partir del catalogo de {@link Permiso}.
 *
 * <p>El catalogo sigue siendo fijo a proposito: se pueden combinar los permisos
 * como se quiera, pero no inventar unos nuevos. Un permiso que ningun sitio del
 * codigo comprueba seria una casilla que no protege nada.
 */
@Entity
@Table(name = "rol")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rol extends EntidadAuditable {

    /** Rol de administracion. Se crea en la migracion y no se toca. */
    public static final long ID_ADMINISTRACION = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    /**
     * Rol de serie: no se puede borrar.
     *
     * <p>El de administracion, ademas, no se puede editar: es el que reparte los
     * permisos, y dejar que se quite el suyo propio es la forma mas rapida de
     * quedarse fuera del programa para siempre.
     */
    @Column(name = "sistema", nullable = false)
    private boolean sistema;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    /**
     * Los permisos concedidos.
     *
     * <p>Se cargan siempre con el rol ({@code EAGER}) porque no hay ni un solo
     * caso en que interese el rol sin ellos: se pide para saber que puede hacer
     * alguien.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rol_permiso", joinColumns = @JoinColumn(name = "rol_id"))
    @Column(name = "permiso", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Set<Permiso> permisos = EnumSet.noneOf(Permiso.class);

    // ==================================================================
    // Alta y mantenimiento
    // ==================================================================

    public static Rol crear(String nombre, String descripcion, Set<Permiso> permisos) {
        Rol rol = new Rol();
        rol.nombre = exigirNombre(nombre);
        rol.descripcion = textoONulo(descripcion);
        rol.sistema = false;
        rol.activo = true;
        rol.permisos = copiaDe(permisos);
        return rol;
    }

    /**
     * Cambia nombre, descripcion y permisos de golpe.
     *
     * <p>Es una sustitucion y no un anadido: la pantalla de roles manda la lista
     * completa de casillas marcadas, asi que desmarcar una tiene que quitarla.
     */
    public void actualizar(String nombre, String descripcion, Set<Permiso> permisos) {
        exigirEditable();
        this.nombre = exigirNombre(nombre);
        this.descripcion = textoONulo(descripcion);
        this.permisos = copiaDe(permisos);
    }

    public void activar() {
        this.activo = true;
    }

    /**
     * Cierra el rol para nuevas asignaciones.
     *
     * <p>Quien ya lo tenga lo conserva: el servicio comprueba antes que no quede
     * nadie con el.
     */
    public void desactivar() {
        exigirEditable();
        this.activo = false;
    }

    /**
     * Mantiene al dia al rol de administracion.
     *
     * <p>Se llama al arrancar. Un permiso nuevo del catalogo tiene que llegarle
     * solo: si hubiera que acordarse de marcarlo a mano, la primera funcion que
     * se anadiera dejaria al administrador sin poder usarla.
     */
    public void concederTodo() {
        this.permisos = EnumSet.allOf(Permiso.class);
    }

    public boolean esAdministracion() {
        return id != null && id == ID_ADMINISTRACION;
    }

    public boolean tiene(Permiso permiso) {
        return permisos.contains(permiso);
    }

    public Set<Permiso> getPermisos() {
        return Collections.unmodifiableSet(permisos);
    }

    // ==================================================================

    /**
     * El rol de administracion es intocable.
     *
     * <p>No es una comodidad: es lo que impide que alguien se quede sin poder
     * entrar en el programa por haberse quitado a si mismo el permiso de
     * repartir permisos. Los demas roles se editan sin restriccion.
     */
    private void exigirEditable() {
        if (esAdministracion()) {
            throw new ConflictoException(
                    "El rol de administracion no se puede modificar: es el que reparte los permisos.");
        }
    }

    private static Set<Permiso> copiaDe(Set<Permiso> permisos) {
        if (permisos == null || permisos.isEmpty()) {
            throw new ReglaNegocioException(
                    "Un rol sin ningun permiso no sirve de nada: marca al menos uno.");
        }
        return EnumSet.copyOf(permisos);
    }

    private static String exigirNombre(String nombre) {
        String limpio = textoONulo(nombre);
        if (limpio == null) {
            throw new ReglaNegocioException("El rol necesita un nombre.");
        }
        if (limpio.length() > 50) {
            throw new ReglaNegocioException("El nombre del rol no puede pasar de 50 caracteres.");
        }
        return limpio;
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
