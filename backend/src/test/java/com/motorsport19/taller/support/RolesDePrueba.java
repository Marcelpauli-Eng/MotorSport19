package com.motorsport19.taller.support;

import com.motorsport19.taller.usuario.domain.Permiso;
import com.motorsport19.taller.usuario.domain.Rol;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.EnumSet;
import java.util.Set;

/**
 * Roles listos para los tests.
 *
 * <p>Desde que los roles los compone el administrador, un test no puede decir
 * «este usuario es TECNICO»: tiene que decir que permisos lleva. Estos dos
 * reproducen los dos perfiles de siempre.
 */
public final class RolesDePrueba {

    private RolesDePrueba() {
    }

    /** Todo concedido. */
    public static Rol administracion() {
        return con(1L, "Administracion", EnumSet.allOf(Permiso.class));
    }

    /** Trabaja ordenes, no ve importes y solo ve las suyas. */
    public static Rol taller() {
        return con(3L, "Taller", EnumSet.of(
                Permiso.CLIENTES_VER, Permiso.MOTOS_VER, Permiso.ORDENES_VER,
                Permiso.ORDENES_DIAGNOSTICAR, Permiso.ORDENES_ESTADO,
                Permiso.ORDENES_LINEAS_MANO_OBRA, Permiso.ORDENES_LINEAS_MATERIAL,
                Permiso.ALMACEN_VER));
    }

    public static Rol con(Long id, String nombre, Set<Permiso> permisos) {
        Rol rol = Rol.crear(nombre, null, permisos);
        ReflectionTestUtils.setField(rol, "id", id);
        return rol;
    }
}
