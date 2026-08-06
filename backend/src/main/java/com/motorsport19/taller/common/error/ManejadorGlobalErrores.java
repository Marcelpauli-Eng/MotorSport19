package com.motorsport19.taller.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

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
