package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.usuario.domain.Rol;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * Extrae el rol del token y lo convierte en la autoridad que entiende Spring.
 *
 * <p>El rol viaja dentro del token firmado, asi que no hace falta consultar la
 * base de datos en cada peticion. La contrapartida es que un cambio de rol no
 * surte efecto hasta que el usuario vuelve a entrar; con tokens de ocho horas
 * es un compromiso razonable frente a una consulta por peticion.
 */
public class ConversorAutoridadesToken implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt token) {
        String rol = token.getClaimAsString("rol");
        if (rol == null) {
            return List.of();
        }
        try {
            Rol.valueOf(rol);
        } catch (IllegalArgumentException e) {
            // Un token con un rol que ya no existe no otorga nada.
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority(UsuarioAutenticado.PREFIJO_ROL + rol));
    }
}
