package com.motorsport19.taller.common.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce las excepciones a respuestas HTTP con mensajes en espanol.
 *
 * <p>Presta atencion especial a los errores que llegan desde PostgreSQL: los
 * triggers de integridad de la migracion V6 lanzan mensajes ya redactados en
 * espanol ("Los registros de factura son inmutables...", "Stock insuficiente
 * para la pieza..."). Aqui se recuperan y se devuelven tal cual, en vez de
 * enterrarlos bajo un 500 generico. Esas reglas se cumplen en la base de datos
 * aunque el servicio se despiste, y el usuario merece leer el motivo real.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    /** Violacion de restriccion RESTRICT: la lanzan los triggers de inmutabilidad. */
    private static final String SQLSTATE_RESTRICT_VIOLATION = "23001";
    /** Violacion de CHECK: la lanza, entre otros, el control de stock negativo. */
    private static final String SQLSTATE_CHECK_VIOLATION = "23514";
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";
    private static final String SQLSTATE_FK_VIOLATION = "23503";
    /** Prefijo de la familia "integrity constraint violation". */
    private static final String SQLSTATE_INTEGRIDAD = "23";

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<RespuestaError> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), req);
    }

    /**
     * Ruta que no existe. Cae aqui como {@code NoResourceFoundException} porque
     * Spring intenta servirla como recurso estatico; sin este manejador acababa
     * en el generico y una URL mal escrita devolvia un 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RespuestaError> rutaInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, "Ruta no encontrada",
                "La ruta %s %s no existe.".formatted(req.getMethod(), req.getRequestURI()), req);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<RespuestaError> conflicto(ConflictoException ex, HttpServletRequest req) {
        return construir(HttpStatus.CONFLICT, "Conflicto", ex.getMessage(), req);
    }

    /**
     * Permiso denegado decidido dentro del servicio, no en la cadena de filtros.
     *
     * <p>Es el caso de «esta orden es de otro tecnico»: la ruta si esta permitida
     * para su rol, lo que no le pertenece es ese registro concreto. Sin este
     * manejador la excepcion caia en el generico y el usuario recibia un 500 con
     * un mensaje inutil.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RespuestaError> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Acceso denegado en {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return construir(HttpStatus.FORBIDDEN, "Acceso denegado", ex.getMessage(), req);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<RespuestaError> reglaNegocio(ReglaNegocioException ex, HttpServletRequest req) {
        return construir(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio", ex.getMessage(), req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<RespuestaError> edicionConcurrente(OptimisticLockingFailureException ex,
                                                             HttpServletRequest req) {
        return construir(HttpStatus.CONFLICT, "Edicion concurrente",
                "Otro usuario ha modificado este registro mientras lo editaba. Vuelva a cargarlo e intentelo de nuevo.",
                req);
    }

    /**
     * Dos operaciones peleandose por las mismas filas.
     *
     * <p>Pasa sobre todo con el doble clic en «entrar en reparacion»: las dos
     * peticiones van a bloquear las mismas piezas del almacen y la base de datos
     * corta una para deshacer el abrazo. No se ha perdido ni corrompido nada
     * —justamente por eso lo corta—, asi que no es una averia que haya que
     * comunicar a nadie: es «espera un segundo y repite».
     */
    @ExceptionHandler(org.springframework.dao.CannotAcquireLockException.class)
    public ResponseEntity<RespuestaError> operacionSimultanea(
            org.springframework.dao.CannotAcquireLockException ex, HttpServletRequest req) {
        log.warn("Operacion simultanea sobre los mismos datos en {} {}", req.getMethod(), req.getRequestURI());
        return construir(HttpStatus.CONFLICT, "Operacion simultanea",
                "Se estaba haciendo esta misma operacion desde otro sitio. No se ha cambiado nada: "
                + "espere un momento y vuelva a intentarlo.", req);
    }

    /**
     * Errores que vienen de la base de datos. Se busca en la cadena de causas la
     * {@link SQLException} original para leer su SQLSTATE y su mensaje.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RespuestaError> integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        SQLException sql = buscarSqlException(ex);
        String sqlState = sql != null ? sql.getSQLState() : null;
        String mensaje = mensajeLegibleDeBaseDeDatos(sql);

        if (sqlState == null) {
            log.warn("Violacion de integridad sin SQLSTATE identificable", ex);
            return construir(HttpStatus.CONFLICT, "Conflicto de datos",
                    "La operacion no se ha podido completar porque incumple una restriccion de integridad.", req);
        }

        // Las reglas de dominio impuestas por triggers no son errores del servidor:
        // son respuestas legitimas y el mensaje ya viene redactado en espanol.
        if (SQLSTATE_RESTRICT_VIOLATION.equals(sqlState) || SQLSTATE_CHECK_VIOLATION.equals(sqlState)) {
            return construir(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio", mensaje, req);
        }
        if (SQLSTATE_UNIQUE_VIOLATION.equals(sqlState)) {
            return construir(HttpStatus.CONFLICT, "Registro duplicado",
                    "Ya existe un registro con esos datos. " + mensaje, req);
        }
        if (SQLSTATE_FK_VIOLATION.equals(sqlState)) {
            return construir(HttpStatus.CONFLICT, "Referencia en uso",
                    "No se puede completar la operacion porque el registro esta referenciado por otros datos.", req);
        }
        if (sqlState.startsWith(SQLSTATE_INTEGRIDAD)) {
            return construir(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio", mensaje, req);
        }

        log.warn("Violacion de integridad no contemplada (SQLSTATE {})", sqlState, ex);
        return construir(HttpStatus.CONFLICT, "Conflicto de datos", mensaje, req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaError> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> detalles = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> detalles.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> detalles.putIfAbsent(e.getObjectName(), e.getDefaultMessage()));

        return ResponseEntity.badRequest().body(RespuestaError.deValidacion(
                "Hay %d campo(s) con errores de validacion.".formatted(detalles.size()),
                req.getRequestURI(), detalles));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RespuestaError> tipoIncorrecto(MethodArgumentTypeMismatchException ex,
                                                         HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "Parametro no valido",
                "El valor '%s' no es valido para el parametro '%s'.".formatted(ex.getValue(), ex.getName()), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RespuestaError> argumentoInvalido(IllegalArgumentException ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "Datos no validos", ex.getMessage(), req);
    }

    /**
     * Ordenar un listado por una columna que no existe.
     *
     * <p>Llega solo, sin que nadie lo teclee: basta con que la pantalla mande un
     * {@code sort} que ya no se corresponde con el modelo —una columna renombrada,
     * un enlace guardado en favoritos, una version vieja del navegador en cache—.
     * Hibernate lo convierte en un fallo de consulta y el listado entero, que es
     * la pantalla de trabajo, respondia «error inesperado» sin decir por que.
     *
     * <p>Ahora se contesta cual es la columna que no vale, que es informacion
     * accionable: quien lo ve puede quitar la ordenacion y seguir trabajando.
     */
    @ExceptionHandler({PropertyReferenceException.class, InvalidDataAccessApiUsageException.class})
    public ResponseEntity<RespuestaError> ordenacionInvalida(Exception ex, HttpServletRequest req) {
        String campo = nombreDeLaColumna(ex);
        String mensaje = campo == null
                ? "No se ha podido ordenar el listado por el criterio indicado. Quite la ordenacion y vuelva a intentarlo."
                : "No se puede ordenar por '%s': esa columna no existe.".formatted(campo);
        return construir(HttpStatus.BAD_REQUEST, "Ordenacion no valida", mensaje, req);
    }

    /** Saca el nombre de la columna del mensaje de Hibernate o de Spring Data. */
    private static String nombreDeLaColumna(Throwable ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof PropertyReferenceException referencia) {
                return referencia.getPropertyName();
            }
            String texto = causa.getMessage();
            if (texto != null) {
                Matcher coincidencia = ATRIBUTO_DESCONOCIDO.matcher(texto);
                if (coincidencia.find()) {
                    return coincidencia.group(1);
                }
            }
        }
        return null;
    }

    private static final Pattern ATRIBUTO_DESCONOCIDO =
            Pattern.compile("Could not resolve attribute '([^']+)'");

    /**
     * Metodo HTTP que esa direccion no admite.
     *
     * <p>Un DELETE donde solo hay GET y PUT. Nadie lo hace a mano, pero llega:
     * una pantalla vieja que quedo abierta, una extension del navegador, un
     * enlace mal formado. Sin este manejador salia un 500 —«el programa esta
     * roto»— cuando lo unico que pasa es que esa operacion no existe ahi.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RespuestaError> metodoNoPermitido(HttpRequestMethodNotSupportedException ex,
                                                            HttpServletRequest req) {
        String admitidos = ex.getSupportedHttpMethods() == null ? "" : ex.getSupportedHttpMethods().stream()
                .map(Object::toString).collect(Collectors.joining(", "));
        String mensaje = admitidos.isBlank()
                ? "Esta direccion no admite la operacion '%s'.".formatted(ex.getMethod())
                : "Esta direccion no admite la operacion '%s'. Admite: %s."
                        .formatted(ex.getMethod(), admitidos);
        return construir(HttpStatus.METHOD_NOT_ALLOWED, "Operacion no permitida", mensaje, req);
    }

    /**
     * Cuerpo de la peticion que no se puede leer.
     *
     * <p>Una fecha mal escrita, una letra en un campo numerico o un valor que no
     * esta en la lista. Sin este manejador todo eso caia en el «error
     * inesperado, avise al administrador», que es lo peor que se le puede
     * contestar a alguien que solo ha tecleado mal: no dice que campo es, no
     * dice que se esperaba, y sugiere que el programa esta roto cuando lo unico
     * que hay es un dato mal puesto.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespuestaError> cuerpoIlegible(HttpMessageNotReadableException ex,
                                                         HttpServletRequest req) {
        if (ex.getCause() instanceof InvalidFormatException formato) {
            String campo = nombreDelCampo(formato);
            Class<?> tipo = formato.getTargetType();

            String mensaje = tipo != null && tipo.isEnum()
                    ? "El valor '%s' no vale para '%s'. Los admitidos son: %s.".formatted(
                            formato.getValue(), campo,
                            Arrays.stream(tipo.getEnumConstants()).map(String::valueOf)
                                    .collect(Collectors.joining(", ")))
                    : "El valor '%s' no es valido para '%s'.".formatted(formato.getValue(), campo);

            return ResponseEntity.badRequest().body(RespuestaError.deValidacion(
                    mensaje, req.getRequestURI(), Map.of(campo, mensaje)));
        }

        return construir(HttpStatus.BAD_REQUEST, "Datos no validos",
                "No se han podido leer los datos enviados. Revise que estan completos y bien escritos.",
                req);
    }

    /** El nombre del campo que venia mal, tal y como se llama en la peticion. */
    private String nombreDelCampo(InvalidFormatException ex) {
        return ex.getPath().stream()
                .map(com.fasterxml.jackson.databind.JsonMappingException.Reference::getFieldName)
                .filter(java.util.Objects::nonNull)
                .reduce((primero, ultimo) -> ultimo)
                .orElse("el dato enviado");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaError> errorInesperado(Exception ex, HttpServletRequest req) {
        log.error("Error inesperado atendiendo {} {}", req.getMethod(), req.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Se ha producido un error inesperado. Si persiste, avise al administrador del sistema.", req);
    }

    // ------------------------------------------------------------------

    private ResponseEntity<RespuestaError> construir(HttpStatus estado, String error, String mensaje,
                                                     HttpServletRequest req) {
        return ResponseEntity.status(estado)
                .body(RespuestaError.de(estado.value(), error, mensaje, req.getRequestURI()));
    }

    private SQLException buscarSqlException(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }

    /**
     * Extrae el mensaje util de un error de PostgreSQL.
     *
     * <p>El driver devuelve algo como {@code "ERROR: Stock insuficiente...\n  Where: PL/pgSQL function..."}.
     * Nos quedamos con la primera linea sin el prefijo tecnico: es exactamente el
     * texto que escribimos en el trigger.
     */
    private String mensajeLegibleDeBaseDeDatos(SQLException sql) {
        if (sql == null || sql.getMessage() == null) {
            return "La operacion incumple una restriccion de la base de datos.";
        }
        String primeraLinea = sql.getMessage().split("\\R", 2)[0].trim();
        if (primeraLinea.startsWith("ERROR:")) {
            primeraLinea = primeraLinea.substring("ERROR:".length()).trim();
        }
        return primeraLinea.isBlank()
                ? "La operacion incumple una restriccion de la base de datos."
                : primeraLinea;
    }
}
