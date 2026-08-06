package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga el usuario para que Spring Security compruebe sus credenciales.
 */
@Service
public class ServicioDetallesUsuario implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public ServicioDetallesUsuario(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .map(UsuarioAutenticado::de)
                // El mensaje es deliberadamente vago y el mismo tanto si el
                // usuario no existe como si la contrasena es incorrecta: decir
                // "ese usuario no existe" permite averiguar quien trabaja aqui.
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales incorrectas"));
    }
}
