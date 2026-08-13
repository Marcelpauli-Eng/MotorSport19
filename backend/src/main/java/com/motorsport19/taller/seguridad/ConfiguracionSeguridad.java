package com.motorsport19.taller.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Autenticacion y autorizacion.
 *
 * <p>Los tres roles del taller y lo que puede hacer cada uno:
 *
 * <ul>
 *   <li><b>ADMIN</b>: todo, incluidos la configuracion fiscal, los precios y el
 *       alta de usuarios.</li>
 *   <li><b>MOSTRADOR</b>: clientes, motos, ordenes de trabajo y facturacion.
 *       El inventario solo lo consulta.</li>
 *   <li><b>TECNICO</b>: sus ordenes de trabajo, el diagnostico y el estado. Al
 *       mover una orden a reparacion consume piezas, pero no puede dar entradas
 *       ni hacer ajustes de almacen por su cuenta. <b>No ve importes</b>: ni la
 *       tarifa/hora, ni los totales de la orden, ni los precios del catalogo.
 *       Eso no se resuelve solo aqui —una ruta se permite o se deniega entera—
 *       sino tambien en los controladores, que le sirven las mismas fichas con
 *       los campos de dinero vacios.</li>
 * </ul>
 *
 * <p>Las reglas se declaran aqui por ruta, y no con anotaciones repartidas por
 * los controladores, para que todo el mapa de permisos se pueda leer de una vez
 * en un solo sitio.
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    private static final String ADMIN = "ADMIN";
    private static final String MOSTRADOR = "MOSTRADOR";
    private static final String TECNICO = "TECNICO";

    /**
     * Clave con la que se firman los tokens.
     *
     * <p>En produccion llega por variable de entorno. El valor por defecto solo
     * sirve para desarrollo, y el arranque falla si en produccion no se cambia
     * (ver {@link #comprobarClave}).
     */
    private final String claveFirma;
    private final String origenesPermitidos;

    public ConfiguracionSeguridad(
            @Value("${motorsport19.seguridad.clave-jwt:}") String claveFirma,
            @Value("${motorsport19.seguridad.origenes-cors:}") String origenesPermitidos) {
        this.claveFirma = comprobarClave(claveFirma);
        this.origenesPermitidos = origenesPermitidos;
    }

    @Bean
    public SecurityFilterChain cadenaDeFiltros(HttpSecurity http) throws Exception {
        http
            // La API no usa cookies de sesion, solo el token de la cabecera
            // Authorization, asi que no hay superficie para CSRF.
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(configuracionCors()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(rutas -> rutas
                // ----- Publico -----
                .requestMatchers("/auth/login").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // Las peticiones de sondeo del navegador (CORS) van sin token.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ----- Usuarios: solo el administrador -----
                // Mostrador necesita saber a que tecnico asignar el trabajo; el
                // resto de la gestion de usuarios sigue siendo de direccion.
                .requestMatchers(HttpMethod.GET, "/usuarios/tecnicos").hasAnyRole(ADMIN, MOSTRADOR)
                .requestMatchers("/usuarios/**").hasRole(ADMIN)

                // ----- Configuracion fiscal y precios: solo el administrador -----
                // Leerla si la abre mostrador: son los datos que van impresos en
                // cada factura, y los necesita para comprobar antes de emitir.
                .requestMatchers(HttpMethod.GET, "/configuracion").hasAnyRole(ADMIN, MOSTRADOR)
                .requestMatchers("/configuracion/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.PUT, "/piezas/*/precios").hasRole(ADMIN)

                // El precio de la hora de una orden se negocia con el cliente, y
                // eso pasa en el mostrador. Un tecnico apunta horas, no las tarifa.
                .requestMatchers(HttpMethod.PUT, "/ordenes/*/tarifa-hora").hasAnyRole(ADMIN, MOSTRADOR)
                .requestMatchers(HttpMethod.PUT, "/ordenes/*/lineas/*/precio").hasAnyRole(ADMIN, MOSTRADOR)
                // Un descuento es dinero: lo pacta quien atiende al cliente.
                .requestMatchers(HttpMethod.PUT, "/ordenes/*/descuento-general").hasAnyRole(ADMIN, MOSTRADOR)
                .requestMatchers(HttpMethod.PUT, "/ordenes/*/lineas/*/descuento").hasAnyRole(ADMIN, MOSTRADOR)

                .requestMatchers(HttpMethod.POST, "/piezas").hasRole(ADMIN)
                .requestMatchers(HttpMethod.PUT, "/piezas/**").hasRole(ADMIN)

                // ----- Almacen: las entradas y los ajustes los firma el administrador -----
                // El consumo de piezas en una orden no pasa por aqui: lo genera
                // el propio flujo de la OT, que si puede lanzar un tecnico.
                .requestMatchers(HttpMethod.POST, "/inventario/piezas/*/entradas").hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, "/inventario/piezas/*/ajustes").hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, "/inventario/piezas/*/salidas").hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, "/proveedores/**").hasAnyRole(ADMIN, MOSTRADOR)
                .requestMatchers(HttpMethod.PUT, "/proveedores/**").hasAnyRole(ADMIN, MOSTRADOR)

                // ----- Servicios tipo: los define direccion, los usa todo el taller -----
                // Una plantilla fija cuantas horas se cobran por una revision: es
                // una decision de precio, aunque aqui no aparezca ningun euro. La
                // consulta si la abre cualquiera, porque el desplegable que las
                // vuelca en la OT lo usan el mostrador y el propio tecnico.
                .requestMatchers(HttpMethod.GET, "/servicios-tipo/**").authenticated()
                .requestMatchers("/servicios-tipo/**").hasRole(ADMIN)

                // ----- Agenda: la consulta todo el taller, la gestiona mostrador -----
                // Un tecnico necesita ver que entra manana para organizarse el dia;
                // dar y mover citas es trabajo de quien coge el telefono.
                .requestMatchers(HttpMethod.GET, "/citas/**").authenticated()
                .requestMatchers("/citas/**").hasAnyRole(ADMIN, MOSTRADOR)

                // ----- Facturacion: mostrador y administrador -----
                .requestMatchers("/facturas/**").hasAnyRole(ADMIN, MOSTRADOR)
                .requestMatchers("/facturacion/**").hasAnyRole(ADMIN, MOSTRADOR)

                // Los informes son datos economicos: margenes, compras y IVA.
                // Un tecnico no tiene por que ver cuanto gana el taller.
                .requestMatchers("/estadisticas/**").hasAnyRole(ADMIN, MOSTRADOR)

                // ----- Clientes y motos: los gestiona mostrador -----
                // El tecnico los CONSULTA, porque necesita saber de quien es la
                // moto que tiene delante, pero no los modifica.
                .requestMatchers(HttpMethod.GET, "/clientes/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/motos/**").authenticated()
                .requestMatchers("/clientes/**").hasAnyRole(ADMIN, MOSTRADOR)
                .requestMatchers("/motos/**").hasAnyRole(ADMIN, MOSTRADOR)

                // ----- Ordenes de trabajo -----
                // Abrirlas y cerrarlas es cosa de mostrador; trabajarlas, del
                // tecnico. El filtro por tecnico asignado se aplica ademas en
                // el servicio, porque eso ya no es cuestion de rutas.
                .requestMatchers(HttpMethod.POST, "/ordenes").hasAnyRole(ADMIN, MOSTRADOR)
                .requestMatchers("/ordenes/*/entrega").hasAnyRole(ADMIN, MOSTRADOR)
                .requestMatchers("/ordenes/*/aprobacion").hasAnyRole(ADMIN, MOSTRADOR)
                .requestMatchers("/ordenes/*/rechazo").hasAnyRole(ADMIN, MOSTRADOR)
                // Dejar una orden preparada es repartir trabajo, y el trabajo lo
                // reparte quien lo ha vendido. Un tecnico la ejecuta, no se la da.
                .requestMatchers("/ordenes/*/preparacion").hasAnyRole(ADMIN, MOSTRADOR)
                // El presupuesto en PDF lleva los precios impresos: es el papel
                // que se le enseña al cliente, no al taller. Los importes que un
                // tecnico no ve en pantalla tampoco pueden salir por aqui.
                .requestMatchers(HttpMethod.GET, "/ordenes/*/presupuesto/pdf").hasAnyRole(ADMIN, MOSTRADOR)
                .requestMatchers("/ordenes/**").authenticated()

                // ----- Inventario: todos consultan -----
                .requestMatchers(HttpMethod.GET, "/inventario/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/piezas/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/proveedores/**").authenticated()

                .anyRequest().authenticated())

            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDeToken())))

            // Respuestas en JSON, con el mismo formato que el resto de errores,
            // en vez de la pagina de login de Spring.
            .exceptionHandling(errores -> errores
                .authenticationEntryPoint(new PuntoEntradaNoAutenticado())
                .accessDeniedHandler(new ManejadorAccesoDenegado()));

        return http.build();
    }

    /**
     * Convierte el token en el {@link UsuarioAutenticado} que espera el resto de
     * la aplicacion, en vez del {@code Jwt} crudo de Spring.
     */
    @Bean
    public JwtAuthenticationConverter conversorDeToken() {
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(new ConversorAutoridadesToken());
        return conversor;
    }

    @Bean
    public PasswordEncoder codificadorPasswords() {
        // Coste 10: unos 100 ms por comprobacion en hardware modesto. Suficiente
        // para que probar contrasenas en masa sea inviable sin que el login se
        // note lento en el mini-PC del taller.
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager gestorAutenticacion(ServicioDetallesUsuario detalles,
                                                     PasswordEncoder codificador) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(detalles);
        proveedor.setPasswordEncoder(codificador);
        // Compara siempre el hash aunque el usuario no exista, para que el
        // tiempo de respuesta no delate que usuarios son validos.
        proveedor.setHideUserNotFoundExceptions(true);
        return new org.springframework.security.authentication.ProviderManager(proveedor);
    }

    @Bean
    public JwtEncoder codificadorJwt() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveSecreta()));
    }

    @Bean
    public JwtDecoder decodificadorJwt() {
        return NimbusJwtDecoder.withSecretKey(claveSecreta())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Origenes permitidos para peticiones desde otro dominio.
     *
     * <p>Con el montaje recomendado no hace falta: tanto en el taller (nginx)
     * como en Vercel, el frontend y la API se sirven bajo el mismo dominio. Se
     * deja configurable por si se separan.
     */
    @Bean
    public CorsConfigurationSource configuracionCors() {
        CorsConfiguration config = new CorsConfiguration();

        if (origenesPermitidos == null || origenesPermitidos.isBlank()) {
            // Sin origenes configurados no se permite ninguno. Un comodin aqui
            // dejaria que cualquier web hiciera peticiones con el token del
            // usuario.
            config.setAllowedOrigins(List.of());
        } else {
            config.setAllowedOrigins(Arrays.stream(origenesPermitidos.split(","))
                    .map(String::trim)
                    .filter(o -> !o.isEmpty())
                    .toList());
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource origen = new UrlBasedCorsConfigurationSource();
        origen.registerCorsConfiguration("/**", config);
        return origen;
    }

    private SecretKeySpec claveSecreta() {
        return new SecretKeySpec(claveFirma.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * Exige una clave de firma de longitud suficiente.
     *
     * <p>HMAC-SHA256 admite claves cortas, pero una clave corta se puede romper
     * por fuerza bruta y con ella se pueden fabricar tokens de administrador. Es
     * preferible que la aplicacion no arranque a que arranque siendo insegura.
     */
    private static String comprobarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new IllegalStateException("""
                    Falta la clave de firma de los tokens.
                    Defina motorsport19.seguridad.clave-jwt (o la variable de entorno
                    MOTORSPORT19_SEGURIDAD_CLAVE_JWT) con al menos 32 caracteres.
                    Puede generarla con:  openssl rand -base64 48""");
        }
        if (clave.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "La clave de firma de los tokens debe tener al menos 32 caracteres. "
                    + "Genere una con: openssl rand -base64 48");
        }
        return clave;
    }
}
