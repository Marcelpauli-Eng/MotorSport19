package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.usuario.domain.Rol;
import com.motorsport19.taller.usuario.domain.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Usuario autenticado, tal y como lo ve Spring Security.
 *
 * <p>Envuelve al {@link Usuario} del dominio en vez de sustituirlo: el dominio
 * no tiene por que saber nada de Spring Security.
 */
public record UsuarioAutenticado(
        Long id,
        String username,
        String nombreCompleto,
        Rol rol,
        String passwordHash,
        boolean activo
) implements UserDetails {

    /** Prefijo que Spring Security espera en los roles. */
    public static final String PREFIJO_ROL = "ROLE_";

    public static UsuarioAutenticado de(Usuario usuario) {
        return new UsuarioAutenticado(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getRol(),
                usuario.getPasswordHash(),
                usuario.isActivo());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(PREFIJO_ROL + rol.name()));
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
