package com.motorsport19.taller.usuario.web;

import com.motorsport19.taller.usuario.domain.Permiso;
import com.motorsport19.taller.usuario.domain.Rol;
import com.motorsport19.taller.usuario.service.RolService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Roles del taller y el catalogo de permisos con el que se componen.
 */
@RestController
@RequestMapping("/roles")
public class RolController {

    private final RolService rolService;

    public RolController(RolService rolService) {
        this.rolService = rolService;
    }

    /**
     * El catalogo de permisos, agrupado como se pinta en la pantalla.
     *
     * <p>Sale del enum del backend y no de una lista repetida en el frontend:
     * si discrepasen, la pantalla ofreceria casillas que no protegen nada o
     * esconderia permisos que si se comprueban.
     */
    @GetMapping("/permisos")
    public List<GrupoPermisos> catalogo() {
        Map<Permiso.Grupo, List<Permiso>> porGrupo = Permiso.todos().stream()
                .collect(Collectors.groupingBy(Permiso::getGrupo, java.util.LinkedHashMap::new,
                        Collectors.toList()));

        return porGrupo.entrySet().stream()
                .map(e -> new GrupoPermisos(
                        e.getKey().name(),
                        e.getKey().getTitulo(),
                        e.getValue().stream()
                                .map(p -> new PermisoResponse(p.name(), p.getDescripcion(), p.getDetalle()))
                                .toList()))
                .toList();
    }

    @GetMapping
    public List<RolResponse> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
        return rolService.listar(soloActivos).stream()
                .map(r -> RolResponse.de(r, rolService.usuariosDe(r.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public RolResponse obtener(@PathVariable Long id) {
        Rol rol = rolService.obtener(id);
        return RolResponse.de(rol, rolService.usuariosDe(id));
    }

    @PostMapping
    public ResponseEntity<RolResponse> crear(@Valid @RequestBody GuardarRolRequest peticion,
                                             UriComponentsBuilder uriBuilder) {
        Rol rol = rolService.crear(peticion.nombre(), peticion.descripcion(), peticion.permisos());
        return ResponseEntity
                .created(uriBuilder.path("/roles/{id}").build(rol.getId()))
                .body(RolResponse.de(rol, 0));
    }

    @PutMapping("/{id}")
    public RolResponse actualizar(@PathVariable Long id,
                                  @Valid @RequestBody GuardarRolRequest peticion) {
        Rol rol = rolService.actualizar(id, peticion.nombre(), peticion.descripcion(), peticion.permisos());
        return RolResponse.de(rol, rolService.usuariosDe(id));
    }

    /** Abre o cierra el rol para nuevas asignaciones. */
    @PutMapping("/{id}/estado")
    public RolResponse cambiarEstado(@PathVariable Long id, @RequestParam boolean activo) {
        Rol rol = rolService.cambiarEstado(id, activo);
        return RolResponse.de(rol, rolService.usuariosDe(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable Long id) {
        rolService.borrar(id);
    }

    // ------------------------------------------------------------------

    public record GuardarRolRequest(
            @NotBlank(message = "El rol necesita un nombre")
            @Size(max = 50, message = "El nombre no puede pasar de 50 caracteres")
            String nombre,

            @Size(max = 200, message = "La descripcion no puede pasar de 200 caracteres")
            String descripcion,

            @NotEmpty(message = "Un rol sin ningun permiso no sirve de nada: marca al menos uno")
            Set<Permiso> permisos) {
    }

    /**
     * @param usuarios     cuantos lo llevan ahora mismo; con gente dentro no se
     *                     cierra ni se borra
     * @param editable     el de administracion no se toca: es el que reparte los
     *                     permisos y quitarselo dejaria al taller sin nadie que
     *                     pudiera devolverselos
     */
    public record RolResponse(
            Long id,
            String nombre,
            String descripcion,
            boolean sistema,
            boolean activo,
            boolean editable,
            boolean borrable,
            long usuarios,
            Set<Permiso> permisos) {

        static RolResponse de(Rol rol, long usuarios) {
            return new RolResponse(
                    rol.getId(), rol.getNombre(), rol.getDescripcion(),
                    rol.isSistema(), rol.isActivo(),
                    !rol.esAdministracion(),
                    !rol.isSistema() && usuarios == 0,
                    usuarios,
                    rol.getPermisos());
        }
    }

    public record GrupoPermisos(String clave, String titulo, List<PermisoResponse> permisos) {
    }

    public record PermisoResponse(String clave, String descripcion, String detalle) {
    }
}
