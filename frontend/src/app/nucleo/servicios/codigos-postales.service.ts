import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, shareReplay, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface DatosCodigoPostal {
  codigoPostal: string;
  /** La de los dos primeros dígitos. Nula si aún no hay dos. */
  provincia: string | null;
  /** La ciudad cuando no hay duda. Nula si hay varias o ninguna. */
  ciudad: string | null;
  /** Todas las posibles, para ofrecer a elegir cuando hay más de una. */
  ciudades: string[];
}

/**
 * Qué provincia y qué ciudad corresponden a un código postal.
 *
 * <p>Se pregunta mientras el usuario teclea, así que hay dos cosas que importan
 * más que acertar: no molestar y no estorbar.
 *
 * <ul>
 *   <li>Lo consultado se guarda aquí. Un taller repite los mismos códigos
 *       postales de su zona todos los días, y sin esto cada alta volvería a
 *       preguntar por el mismo sitio.</li>
 *   <li>Si la consulta falla —sin red, servidor caído— devuelve vacío en vez de
 *       propagar el error. Que no se pueda sugerir la ciudad no es un problema
 *       del alta: se escribe a mano, como siempre.</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class CodigosPostalesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/codigos-postales`;

  /** Código postal ya consultado -> lo que contestó. */
  private readonly memoria = new Map<string, DatosCodigoPostal>();
  /** Consultas en vuelo, para que dos teclas seguidas no pidan lo mismo dos veces. */
  private readonly enCurso = new Map<string, Observable<DatosCodigoPostal>>();

  /**
   * Consulta un código postal, completo o a medio escribir.
   *
   * <p>Con dos dígitos ya devuelve la provincia y no llega a salir a la red.
   */
  consultar(codigoPostal: string): Observable<DatosCodigoPostal> {
    const digitos = (codigoPostal ?? '').replace(/\D/g, '');
    if (digitos.length < 2) {
      return of(vacio(digitos));
    }

    const recordado = this.memoria.get(digitos);
    if (recordado) {
      return of(recordado);
    }

    const enVuelo = this.enCurso.get(digitos);
    if (enVuelo) {
      return enVuelo;
    }

    const peticion = this.http.get<DatosCodigoPostal>(`${this.base}/${digitos}`).pipe(
      tap((datos) => {
        this.memoria.set(digitos, datos);
        this.enCurso.delete(digitos);
      }),
      catchError(() => {
        this.enCurso.delete(digitos);
        return of(vacio(digitos));
      }),
      shareReplay(1),
    );

    this.enCurso.set(digitos, peticion);
    return peticion;
  }
}

function vacio(codigoPostal: string): DatosCodigoPostal {
  return { codigoPostal, provincia: null, ciudad: null, ciudades: [] };
}
