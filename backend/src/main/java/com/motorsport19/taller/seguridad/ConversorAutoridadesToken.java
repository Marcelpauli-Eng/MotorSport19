package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.usuario.domain.Permiso;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * Extrae los permisos del token y los convierte en las autoridades que entiende
 * Spring.
 *
 * <p>Antes viajaba el rol y las reglas de ruta preguntaban por
 * {@code ROLE_ADMIN}. Con los roles clavados en el codigo servia; ahora que los
 * compone el administrador, lo que viaja y lo que se comprueba es el permiso
 * concreto. Asi un rol nuevo funciona sin tocar la configuracion de seguridad.
 *
 * <p>Los permisos van dentro del token firmado, de modo que autorizar no cuesta
 * ninguna consulta. La contrapartida es que retocar un rol no afecta a quien ya
 * esta dentro hasta que vuelve a entrar; con tokens de ocho horas es el mismo
 * compromiso que ya se aceptaba para el rol.
 */
public class ConversorAutoridadesToken implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt token) {
        List<String> permisos = token.getClaimAsStringList("permisos");
        if (permisos == null) {
            return List.of();
        }
        return permisos.stream()
                // Un permiso retirado del catalogo no otorga nada, pero tampoco
                // invalida el resto del token: se ignora y punto.
                .map(Permiso::deTextoONulo)
                .filter(java.util.Objects::nonNull)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p.name()))
                .toList();
    }
}
