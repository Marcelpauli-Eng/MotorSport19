package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.usuario.domain.Permiso;
import com.motorsport19.taller.usuario.domain.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Usuario autenticado, tal y como lo ve Spring Security.
 *
 * <p>Envuelve al {@link Usuario} del dominio en vez de sustituirlo: el dominio
 * no tiene por que saber nada de Spring Security.
 *
 * <p>Las autoridades son los <b>permisos</b>, no el rol. Antes eran
 * {@code ROLE_ADMIN} y compania, con los tres roles clavados en el codigo; ahora
 * el rol es una fila que compone el administrador y lo que se comprueba en cada
 * ruta es un permiso concreto. Asi un rol nuevo funciona sin tocar ni una linea
 * de la configuracion de seguridad.
 */
public record UsuarioAutenticado(
        Long id,
        String username,
        String nombreCompleto,
        Long rolId,
        String rolNombre,
        Set<Permiso> permisos,
        String passwordHash,
        boolean activo
) implements UserDetails {

    public UsuarioAutenticado {
        permisos = permisos == null || permisos.isEmpty()
                ? EnumSet.noneOf(Permiso.class)
                : EnumSet.copyOf(permisos);
    }

    public static UsuarioAutenticado de(Usuario usuario) {
        return new UsuarioAutenticado(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getRol().getId(),
                usuario.getRol().getNombre(),
                usuario.getRol().getPermisos(),
                usuario.getPasswordHash(),
                usuario.isActivo());
    }

    public boolean tiene(Permiso permiso) {
        return permisos.contains(permiso);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permisos.stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /**
     * Un usuario dado de baja no entra.
     *
     * <p>La baja es logica: sigue existiendo porque firma ordenes de trabajo y
     * movimientos de stock del pasado, pero deja de poder acceder.
     */
    @Override
    public boolean isEnabled() {
        return activo;
    }

    @Override
    public boolean isAccountNonExpired() {
        return activo;
    }

    @Override
    public boolean isAccountNonLocked() {
        return activo;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
