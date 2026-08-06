package com.motorsport19.taller.usuario.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.usuario.domain.Rol;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Alta y mantenimiento de los usuarios del taller.
 *
 * <p>La contrasena en claro no se guarda ni se registra en ningun sitio: entra
 * por parametro, se convierte en hash BCrypt y se descarta.
 */
@Service
public class UsuarioService {

    /** Longitud minima. Corta de mas y BCrypt no salva nada. */
    private static final int LONGITUD_MINIMA_PASSWORD = 8;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder codificador;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder codificador) {
        this.usuarioRepository = usuarioRepository;
        this.codificador = codificador;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Usuario obtener(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el usuario", id));
    }

    @Transactional
    public Usuario crear(String username, String password, String nombreCompleto, String email,
                         String telefono, Rol rol) {
        String usuarioLimpio = username == null ? "" : username.trim().toLowerCase();

        if (usuarioRepository.findByUsername(usuarioLimpio).isPresent()) {
            throw new ConflictoException("Ya existe un usuario '%s'.".formatted(usuarioLimpio));
        }
        comprobarPassword(password);

        Usuario usuario = Usuario.crear(usuarioLimpio, codificador.encode(password), nombreCompleto,
                email, telefono, rol);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario actualizarDatos(Long id, String nombreCompleto, String email, String telefono,
                                   Rol rol) {
        Usuario usuario = obtener(id);
        usuario.actualizarDatos(nombreCompleto, email, telefono);
        if (rol != null) {
            usuario.cambiarRol(rol);
        }
        return usuario;
    }

    /**
     * Cambio de contrasena por el propio usuario.
     *
     * <p>Exige la actual: si alguien deja la sesion abierta en el mostrador, no
     * puede aprovecharse para apropiarse de la cuenta.
     */
    @Transactional
    public void cambiarPassword(Long id, String passwordActual, String passwordNueva) {
        Usuario usuario = obtener(id);

        if (!codificador.matches(passwordActual, usuario.getPasswordHash())) {
            throw new ReglaNegocioException("La contrasena actual no es correcta.");
        }
        comprobarPassword(passwordNueva);
        if (codificador.matches(passwordNueva, usuario.getPasswordHash())) {
            throw new ReglaNegocioException("La contrasena nueva debe ser distinta de la actual.");
        }

        usuario.cambiarPassword(codificador.encode(passwordNueva));
    }

    /** Restablecimiento por el administrador, sin conocer la contrasena anterior. */
    @Transactional
    public void restablecerPassword(Long id, String passwordNueva) {
        comprobarPassword(passwordNueva);
        obtener(id).cambiarPassword(codificador.encode(passwordNueva));
    }

    @Transactional
    public Usuario darDeBaja(Long id, Long idSolicitante) {
        if (id.equals(idSolicitante)) {
            // Sin esto, el unico administrador puede dejarse fuera del sistema.
            throw new ConflictoException("No puede darse de baja a si mismo.");
        }
        Usuario usuario = obtener(id);
        if (usuario.getRol() == Rol.ADMIN && contarAdministradoresActivos() <= 1) {
            throw new ConflictoException(
                    "Es el ultimo administrador activo: nadie podria volver a configurar el sistema.");
        }
        usuario.darDeBaja();
        return usuario;
    }

    @Transactional
    public Usuario reactivar(Long id) {
        Usuario usuario = obtener(id);
        usuario.reactivar();
        return usuario;
    }

    /** Deja constancia del ultimo acceso. */
    @Transactional
    public void registrarAcceso(Long id) {
        usuarioRepository.findById(id).ifPresent(Usuario::registrarAcceso);
    }

    private long contarAdministradoresActivos() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Rol.ADMIN && u.isActivo())
                .count();
    }

    private void comprobarPassword(String password) {
        if (password == null || password.length() < LONGITUD_MINIMA_PASSWORD) {
            throw new ReglaNegocioException(
                    "La contrasena debe tener al menos %d caracteres.".formatted(LONGITUD_MINIMA_PASSWORD));
        }
    }
}
