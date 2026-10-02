package com.motorsport19.taller.orden.service;

import com.motorsport19.taller.common.error.ConflictoException;

/**
 * Se quiere dar por lista una orden con material que no esta en el almacen.
 *
 * <p>No es un no rotundo: la pantalla lo pregunta y, si se confirma, repite la
 * peticion pidiendo seguir adelante. Por eso tiene su propio tipo, para que el
 * aviso se distinga de cualquier otro conflicto.
 */
public class MaterialSinMontarException extends ConflictoException {

    public MaterialSinMontarException(String mensaje) {
        super(mensaje);
    }
}
