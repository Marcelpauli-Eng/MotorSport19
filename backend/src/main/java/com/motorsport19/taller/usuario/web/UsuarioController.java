package com.motorsport19.taller.usuario.web;

import com.motorsport19.taller.seguridad.UsuarioActual;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;

/**
 * Gestion de los usuarios del taller. Reservada al administrador.
 */
@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioActual usuarioActual;

    public UsuarioController(UsuarioService usuarioService, UsuarioActual usuarioActual) {
        this.usuarioService = usuarioService;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioService.listar().stream().map(UsuarioResponse::de).toList();
    }

    /**
     * Tecnicos activos, para asignarlos a una orden.
     *
     * <p>Va aparte del listado de usuarios porque ese es solo de direccion, y
     * mostrador necesita saber a quien puede asignar el trabajo. Solo devuelve
     * el nombre: ni correos, ni telefonos, ni cuando entro por ultima vez.
     */
    @GetMapping("/tecnicos")
    public List<TecnicoResponse> tecnicos() {
        return usuarioService.tecnicosActivos().stream()
                .map(u -> new TecnicoResponse(u.getId(), u.getNombreCompleto()))
                .toList();
    }

    public record TecnicoResponse(Long id, String nombreCompleto) {
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return UsuarioResponse.de(usuarioService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest peticion,
                                                 UriComponentsBuilder uriBuilder) {
        Usuario usuario = usuarioService.crear(peticion.username(), peticion.password(),
                peticion.nombreCompleto(), peticion.email(), peticion.telefono(), peticion.rolId());

        return ResponseEntity
                .created(uriBuilder.path("/usuarios/{id}").build(usuario.getId()))
                .body(UsuarioResponse.de(usuario));
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody ActualizarUsuarioRequest peticion) {
        return UsuarioResponse.de(usuarioService.actualizarDatos(id, peticion.nombreCompleto(),
                peticion.email(), peticion.telefono(), peticion.rolId()));
    }

    /** Restablecimiento por el administrador, sin conocer la contrasena anterior. */
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> restablecerPassword(@PathVariable Long id,
                                                    @Valid @RequestBody PasswordRequest peticion) {
        usuarioService.restablecerPassword(id, peticion.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/baja")
    public UsuarioResponse darDeBaja(@PathVariable Long id) {
        return UsuarioResponse.de(usuarioService.darDeBaja(id, usuarioActual.id()));
    }

    @PostMapping("/{id}/reactivacion")
    public UsuarioResponse reactivar(@PathVariable Long id) {
        return UsuarioResponse.de(usuarioService.reactivar(id));
    }

    // ------------------------------------------------------------------

    public record CrearUsuarioRequest(
            @NotBlank(message = "El nombre de usuario es obligatorio")
            @Size(max = 50, message = "El nombre de usuario no puede superar los 50 caracteres")
            String username,

            @NotBlank(message = "La contrasena es obligatoria")
            @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
            String password,

            @NotBlank(message = "El nombre completo es obligatorio")
            @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
            String nombreCompleto,

            @Email(message = "El email no tiene un formato valido")
            String email,

            @Size(max = 30, message = "El telefono no puede superar los 30 caracteres")
            String telefono,

            @NotNull(message = "Hay que asignar un rol")
            Long rolId) {
    }

    public record ActualizarUsuarioRequest(
            @NotBlank(message = "El nombre completo es obligatorio") String nombreCompleto,
            @Email(message = "El email no tiene un formato valido") String email,
            String telefono,
            Long rolId) {
    }

    public record PasswordRequest(
            @NotBlank(message = "La contrasena es obligatoria")
            @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
            String password) {
    }

    /** Nunca incluye el hash de la contrasena. */
    public record UsuarioResponse(Long id, String username, String nombreCompleto, String email,
                                  String telefono, Long rolId, String rol, String rolDescripcion, boolean activo,
                                  Instant ultimoAcceso) {
        static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getUsername(), u.getNombreCompleto(), u.getEmail(),
                    u.getTelefono(), u.getRol().getId(), u.getRol().getNombre(), u.getRol().getDescripcion(), u.isActivo(),
                    u.getUltimoAcceso());
        }
    }
}
