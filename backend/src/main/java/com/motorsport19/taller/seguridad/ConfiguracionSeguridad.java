package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.usuario.domain.Permiso;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
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

    /**
     * Nombre de la autoridad que representa un permiso.
     *
     * <p>Un metodo de una letra a proposito: el mapa de rutas se lee de un
     * vistazo y meter {@code .name()} en cada linea lo llenaria de ruido.
     */
    private static String p(Permiso permiso) {
        return permiso.name();
    }

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
    public SecurityFilterChain cadenaDeFiltros(HttpSecurity http,
                                              UsuarioRepository usuarioRepository) throws Exception {
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

                // Ayuda para rellenar direcciones: que provincia y que ciudad
                // corresponden a un codigo postal. Es informacion publica de
                // Correos y no dice nada del taller, asi que basta con estar
                // dentro; no hace falta permiso de clientes porque tambien la
                // usa el alta de proveedores y la ficha del propio taller.
                .requestMatchers(HttpMethod.GET, "/codigos-postales/**").authenticated()

                // ----- Usuarios y roles -----
                // El listado de quien puede recibir trabajo lo necesita quien
                // reparte ordenes, no solo quien administra el programa.
                .requestMatchers(HttpMethod.GET, "/usuarios/tecnicos").hasAuthority(p(Permiso.ORDENES_ASIGNAR_TECNICO))
                .requestMatchers(HttpMethod.GET, "/roles/permisos").hasAuthority(p(Permiso.ROLES_GESTIONAR))
                // El desplegable de rol del alta de usuarios necesita la lista.
                .requestMatchers(HttpMethod.GET, "/roles/**").hasAuthority(p(Permiso.USUARIOS_GESTIONAR))
                .requestMatchers("/roles/**").hasAuthority(p(Permiso.ROLES_GESTIONAR))
                .requestMatchers("/usuarios/**").hasAuthority(p(Permiso.USUARIOS_GESTIONAR))

                // ----- Ajustes del taller -----
                // Leerlos si los abre mostrador: son los datos que van impresos
                // en cada factura, y los necesita para comprobar antes de emitir.
                .requestMatchers(HttpMethod.GET, "/configuracion").hasAuthority(p(Permiso.AJUSTES_VER))
                .requestMatchers("/configuracion/**").hasAuthority(p(Permiso.AJUSTES_EDITAR))

                // ----- Dinero -----
                // El precio de la hora, los precios cerrados y el IVA de la orden
                // se negocian con el cliente: los toca quien lo atiende.
                .requestMatchers(HttpMethod.PUT, "/ordenes/*/tarifa-hora").hasAuthority(p(Permiso.PRECIOS_EDITAR))
                .requestMatchers(HttpMethod.PUT, "/ordenes/*/tipo-iva").hasAuthority(p(Permiso.PRECIOS_EDITAR))
                .requestMatchers(HttpMethod.PUT, "/ordenes/*/lineas/*/precio").hasAuthority(p(Permiso.PRECIOS_EDITAR))
                .requestMatchers(HttpMethod.PUT, "/ordenes/*/descuento-general").hasAuthority(p(Permiso.DESCUENTOS_APLICAR))
                .requestMatchers(HttpMethod.PUT, "/ordenes/*/lineas/*/descuento").hasAuthority(p(Permiso.DESCUENTOS_APLICAR))
                .requestMatchers(HttpMethod.PUT, "/piezas/*/precios").hasAuthority(p(Permiso.PIEZAS_PRECIOS))

                // ----- Almacen -----
                // El consumo de piezas de una orden no pasa por aqui: lo genera
                // el propio flujo de la OT, que si puede lanzar un tecnico.
                .requestMatchers(HttpMethod.POST, "/piezas").hasAuthority(p(Permiso.PIEZAS_CREAR))
                .requestMatchers(HttpMethod.PUT, "/piezas/**").hasAuthority(p(Permiso.PIEZAS_EDITAR))
                .requestMatchers(HttpMethod.POST, "/inventario/piezas/*/entradas").hasAuthority(p(Permiso.ALMACEN_MOVER))
                .requestMatchers(HttpMethod.POST, "/inventario/piezas/*/ajustes").hasAuthority(p(Permiso.ALMACEN_MOVER))
                .requestMatchers(HttpMethod.POST, "/inventario/piezas/*/salidas").hasAuthority(p(Permiso.ALMACEN_MOVER))
                .requestMatchers(HttpMethod.POST, "/proveedores/**").hasAuthority(p(Permiso.PROVEEDORES_GESTIONAR))
                .requestMatchers(HttpMethod.PUT, "/proveedores/**").hasAuthority(p(Permiso.PROVEEDORES_GESTIONAR))
                .requestMatchers(HttpMethod.GET, "/inventario/**").hasAuthority(p(Permiso.ALMACEN_VER))
                .requestMatchers(HttpMethod.GET, "/piezas/**").hasAuthority(p(Permiso.ALMACEN_VER))
                .requestMatchers(HttpMethod.GET, "/proveedores/**").hasAuthority(p(Permiso.ALMACEN_VER))

                // ----- Plantillas de trabajo -----
                // Definir cuantas horas se cobran por una revision es una decision
                // de precio; volcarlas en una orden lo hace cualquiera.
                .requestMatchers(HttpMethod.GET, "/servicios-tipo/**").hasAuthority(p(Permiso.SERVICIOS_VER))
                .requestMatchers("/servicios-tipo/**").hasAuthority(p(Permiso.SERVICIOS_GESTIONAR))

                // ----- Agenda -----
                .requestMatchers(HttpMethod.GET, "/citas/**").hasAuthority(p(Permiso.AGENDA_VER))
                .requestMatchers("/citas/**").hasAuthority(p(Permiso.AGENDA_GESTIONAR))

                // ----- Facturacion -----
                // Abrir o cerrar una serie es decidir con que numeracion factura
                // el taller. Consultarlas basta con poder emitir.
                .requestMatchers(HttpMethod.POST, "/facturas/series").hasAuthority(p(Permiso.FACTURAS_SERIES))
                .requestMatchers(HttpMethod.PUT, "/facturas/series/**").hasAuthority(p(Permiso.FACTURAS_SERIES))
                .requestMatchers(HttpMethod.POST, "/facturas/*/rectificativas").hasAuthority(p(Permiso.FACTURAS_RECTIFICAR))
                .requestMatchers(HttpMethod.GET, "/facturas/exportacion/**").hasAuthority(p(Permiso.FACTURACION_EXPORTAR))
                .requestMatchers(HttpMethod.POST, "/facturas").hasAuthority(p(Permiso.FACTURAS_EMITIR))
                .requestMatchers(HttpMethod.GET, "/facturas/**").hasAuthority(p(Permiso.FACTURAS_VER))
                .requestMatchers("/facturas/**").hasAuthority(p(Permiso.FACTURAS_EMITIR))
                .requestMatchers("/facturacion/**").hasAuthority(p(Permiso.FACTURACION_EXPORTAR))

                // Los informes son datos economicos: margenes, compras e IVA.
                .requestMatchers("/estadisticas/**").hasAuthority(p(Permiso.INFORMES_VER))

                // ----- Clientes -----
                .requestMatchers(HttpMethod.GET, "/clientes/**").hasAuthority(p(Permiso.CLIENTES_VER))
                .requestMatchers(HttpMethod.POST, "/clientes").hasAuthority(p(Permiso.CLIENTES_CREAR))
                .requestMatchers(HttpMethod.PUT, "/clientes/*/datos-fiscales").hasAuthority(p(Permiso.CLIENTES_DATOS_FISCALES))
                .requestMatchers(HttpMethod.POST, "/clientes/*/baja").hasAuthority(p(Permiso.CLIENTES_BAJA))
                .requestMatchers("/clientes/**").hasAuthority(p(Permiso.CLIENTES_EDITAR))

                // ----- Motos -----
                .requestMatchers(HttpMethod.GET, "/motos/**").hasAuthority(p(Permiso.MOTOS_VER))
                .requestMatchers(HttpMethod.POST, "/motos").hasAuthority(p(Permiso.MOTOS_CREAR))
                .requestMatchers(HttpMethod.PUT, "/motos/*/propietario").hasAuthority(p(Permiso.MOTOS_CAMBIAR_PROPIETARIO))
                .requestMatchers(HttpMethod.POST, "/motos/*/baja").hasAuthority(p(Permiso.MOTOS_BAJA))
                .requestMatchers("/motos/**").hasAuthority(p(Permiso.MOTOS_EDITAR))

                // ----- Ordenes de trabajo -----
                // El filtro por tecnico asignado se aplica ademas en el servicio:
                // eso ya no es cuestion de rutas sino de que datos se devuelven.
                .requestMatchers(HttpMethod.POST, "/ordenes").hasAuthority(p(Permiso.ORDENES_ABRIR))
                .requestMatchers("/ordenes/*/preparacion").hasAuthority(p(Permiso.ORDENES_PREPARAR))
                .requestMatchers("/ordenes/*/entrega").hasAuthority(p(Permiso.ORDENES_ENTREGAR))
                .requestMatchers("/ordenes/*/aprobacion").hasAuthority(p(Permiso.ORDENES_APROBAR))
                .requestMatchers("/ordenes/*/rechazo").hasAuthority(p(Permiso.ORDENES_APROBAR))
                .requestMatchers(HttpMethod.PUT, "/ordenes/*/diagnostico").hasAuthority(p(Permiso.ORDENES_DIAGNOSTICAR))
                .requestMatchers(HttpMethod.PUT, "/ordenes/*/tecnico").hasAuthority(p(Permiso.ORDENES_ASIGNAR_TECNICO))
                .requestMatchers(HttpMethod.POST, "/ordenes/*/lineas/mano-de-obra").hasAuthority(p(Permiso.ORDENES_LINEAS_MANO_OBRA))
                .requestMatchers(HttpMethod.POST, "/ordenes/*/lineas/piezas").hasAuthority(p(Permiso.ORDENES_LINEAS_MATERIAL))
                .requestMatchers(HttpMethod.DELETE, "/ordenes/*/lineas/**").hasAuthority(p(Permiso.ORDENES_LINEAS_QUITAR))
                // El presupuesto en PDF lleva los precios impresos: es el papel
                // que se le enseña al cliente, no al taller.
                .requestMatchers(HttpMethod.GET, "/ordenes/*/presupuesto/pdf").hasAuthority(p(Permiso.IMPORTES_VER))
                .requestMatchers(HttpMethod.GET, "/ordenes/**").hasAuthority(p(Permiso.ORDENES_VER))
                .requestMatchers("/ordenes/**").hasAuthority(p(Permiso.ORDENES_ESTADO))

                .anyRequest().authenticated())

            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDeToken())))

            // Justo despues de leer el token y antes de decidir los permisos de
            // la ruta: asi una baja o un cambio de rol surten efecto en la
            // siguiente peticion y no cuando caduque el token.
            .addFilterAfter(new FiltroSesionViva(usuarioRepository),
                            BearerTokenAuthenticationFilter.class)

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
