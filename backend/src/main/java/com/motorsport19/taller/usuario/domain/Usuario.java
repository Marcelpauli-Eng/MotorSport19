package com.motorsport19.taller.usuario.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Usuario del sistema (personal del taller).
 *
 * <p>Baja logica mediante {@code activo}: un usuario que deja el taller sigue
 * referenciado desde las ordenes de trabajo y los movimientos de stock que
 * firmo, asi que no se borra; simplemente deja de poder entrar.
 *
 * <p>Esta clase nunca ve una contrasena en claro. Recibe el hash ya calculado
 * por quien corresponde ({@code UsuarioService}), de modo que no hay ninguna via
 * por la que una contrasena acabe registrada en un log o en un volcado.
 */
@Entity
@Table(name = "usuario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    /** Hash BCrypt. La contrasena en claro nunca llega a la base de datos. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "ultimo_acceso")
    private Instant ultimoAcceso;

    // ------------------------------------------------------------------

    /**
     * Da de alta un usuario.
     *
     * @param passwordHash hash BCrypt ya calculado, nunca la contrasena en claro
     */
    public static Usuario crear(String username, String passwordHash, String nombreCompleto,
                                String email, String telefono, Rol rol) {
        Usuario usuario = new Usuario();

        String usuarioLimpio = textoONulo(username);
        if (usuarioLimpio == null) {
            throw new ReglaNegocioException("El nombre de usuario es obligatorio.");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new ReglaNegocioException("Falta la contrasena.");
        }
        if (rol == null) {
            throw new ReglaNegocioException("Hay que asignar un rol al usuario.");
        }

        usuario.username = usuarioLimpio.toLowerCase();
        usuario.passwordHash = passwordHash;
        usuario.rol = rol;
        usuario.activo = true;
        usuario.aplicarDatos(nombreCompleto, email, telefono);
        return usuario;
    }

    public void actualizarDatos(String nombreCompleto, String email, String telefono) {
        comprobarActivo();
        aplicarDatos(nombreCompleto, email, telefono);
    }

    public void cambiarRol(Rol nuevoRol) {
        comprobarActivo();
        if (nuevoRol == null) {
            throw new ReglaNegocioException("Hay que asignar un rol al usuario.");
        }
        this.rol = nuevoRol;
    }

    /** @param passwordHash hash BCrypt ya calculado */
    public void cambiarPassword(String passwordHash) {
        comprobarActivo();
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new ReglaNegocioException("Falta la contrasena.");
        }
        this.passwordHash = passwordHash;
    }

    public void registrarAcceso() {
        this.ultimoAcceso = Instant.now();
    }

    public void darDeBaja() {
        if (!activo) {
            throw new ConflictoException("El usuario '%s' ya estaba dado de baja.".formatted(username));
        }
        this.activo = false;
    }

    public void reactivar() {
        if (activo) {
            throw new ConflictoException("El usuario '%s' ya estaba activo.".formatted(username));
        }
        this.activo = true;
    }

    // ------------------------------------------------------------------

    private void aplicarDatos(String nombreCompleto, String email, String telefono) {
        String nombre = textoONulo(nombreCompleto);
        if (nombre == null) {
            throw new ReglaNegocioException("El nombre completo es obligatorio.");
        }
        this.nombreCompleto = nombre;
        this.email = textoONulo(email);
        this.telefono = textoONulo(telefono);
    }

    private void comprobarActivo() {
        if (!activo) {
            throw new ConflictoException(
                    "El usuario '%s' esta dado de baja: reactivelo antes de modificarlo."
                            .formatted(username));
        }
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
