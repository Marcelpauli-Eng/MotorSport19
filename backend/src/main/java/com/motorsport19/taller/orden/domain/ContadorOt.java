package com.motorsport19.taller.orden.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Contador de numeracion de ordenes de trabajo, uno por ejercicio.
 *
 * <p>No se usa una secuencia de PostgreSQL porque las secuencias no son
 * transaccionales: si la transaccion que abre la OT falla, el numero consumido
 * se pierde y queda un hueco. Con un contador en tabla, el incremento hace
 * rollback junto con todo lo demas.
 */
@Entity
@Table(name = "contador_ot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContadorOt {

    @Id
    @Column(name = "ejercicio", nullable = false)
    private Integer ejercicio;

    @Column(name = "ultimo_numero", nullable = false)
    private Integer ultimoNumero;

    public static ContadorOt para(int ejercicio) {
        ContadorOt contador = new ContadorOt();
        contador.ejercicio = ejercicio;
        contador.ultimoNumero = 0;
        return contador;
    }

    /**
     * Consume el siguiente numero.
     *
     * <p>Debe llamarse con la fila bloqueada (SELECT ... FOR UPDATE) para que dos
     * altas simultaneas no obtengan el mismo numero.
     */
    public int consumirSiguiente() {
        this.ultimoNumero = this.ultimoNumero + 1;
        return this.ultimoNumero;
    }
}
