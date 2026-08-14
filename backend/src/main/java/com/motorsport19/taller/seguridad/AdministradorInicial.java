package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.usuario.domain.Rol;
import com.motorsport19.taller.usuario.repository.RolRepository;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import com.motorsport19.taller.usuario.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Crea el primer administrador cuando la base de datos no tiene ningun usuario.
 *
 * <p>Sin esto una instalacion limpia queda inservible: la API exige identificarse
 * para todo, no hay nadie dado de alta, y no se puede entrar a crear a nadie. El
 * problema del huevo y la gallina.
 *
 * <p>Solo actua si la tabla esta vacia. En cuanto existe un usuario —porque se
 * cargaron los datos de demostracion, o porque ya se instalo antes— esta clase no
 * hace nada, asi que no puede pisar una instalacion en marcha.
 *
 * <p>La contrasena sale de {@code MOTORSPORT19_ADMIN_PASSWORD} si esta definida.
 * Si no, se genera una al azar y se escribe en el log de arranque: quien instala
 * el programa esta mirando esa consola, y asi no hay ninguna contrasena por
 * defecto que alguien pueda adivinar leyendo el repositorio.
 */
@Component
public class AdministradorInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

    private static final String USERNAME_POR_DEFECTO = "admin";
    /** 18 bytes en Base64 dan 24 caracteres: de sobra y aun copiable a mano. */
    private static final int BYTES_PASSWORD = 18;

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final UsuarioService usuarioService;
    private final String usernameConfigurado;
    private final String passwordConfigurada;

    public AdministradorInicial(UsuarioRepository usuarioRepository,
                                RolRepository rolRepository,
                                UsuarioService usuarioService,
                                @Value("${motorsport19.admin-inicial.username:}") String usernameConfigurado,
                                @Value("${motorsport19.admin-inicial.password:}") String passwordConfigurada) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.usuarioService = usuarioService;
        this.usernameConfigurado = usernameConfigurado;
        this.passwordConfigurada = passwordConfigurada;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        String username = usernameConfigurado.isBlank() ? USERNAME_POR_DEFECTO : usernameConfigurado.trim();
        boolean generada = passwordConfigurada.isBlank();
        String password = generada ? generarPassword() : passwordConfigurada;

        // El rol de administracion lo crea la migracion V14: aqui solo se
        // comprueba. Si faltara, el taller se quedaria sin poder entrar, asi que
        // es mejor fallar el arranque que crear un usuario sin permisos.
        Rol administracion = rolRepository.findById(Rol.ID_ADMINISTRACION)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el rol de administracion: la base de datos no esta migrada."));

        usuarioService.crear(username, password, "Administrador", null, null, administracion.getId());

        if (generada) {
            // A propósito con adornos: esto tiene que verse entre el ruido del
            // arranque, porque es la unica vez que la contrasena aparece en claro.
            log.warn("""

                    ==========================================================
                     PRIMER ARRANQUE: no habia ningun usuario en la base.
                     Se ha creado un administrador para poder entrar.

                       Usuario:    {}
                       Contrasena: {}

                     Anotela ahora. No vuelve a mostrarse.
                     Entre y cambiela desde «Mi cuenta».
                    ==========================================================
                    """, username, password);
        } else {
            log.info("Primer arranque: creado el administrador '{}' con la contrasena configurada.", username);
        }
    }

    private static String generarPassword() {
        byte[] bytes = new byte[BYTES_PASSWORD];
        new SecureRandom().nextBytes(bytes);
        // Sin caracteres que se confundan al copiarlos de una consola.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
