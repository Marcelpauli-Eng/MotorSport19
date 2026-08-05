import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina } from '../modelos/comunes';
import { AlertaStock, MovimientoStock, Pieza, Proveedor } from '../modelos/taller';
import { SesionService } from './sesion.service';

@Injectable({ providedIn: 'root' })
export class InventarioService {
  private readonly http = inject(HttpClient);
  private readonly sesion = inject(SesionService);
  private readonly basePiezas = `${environment.urlApi}/piezas`;
  private readonly baseInventario = `${environment.urlApi}/inventario`;

  buscarPiezas(
    texto = '',
    opciones: { soloActivas?: boolean; soloBajoMinimo?: boolean; pagina?: number; tamano?: number } = {},
  ): Observable<Pagina<Pieza>> {
    let params = new HttpParams()
      .set('page', opciones.pagina ?? 0)
      .set('size', opciones.tamano ?? 20)
      .set('soloActivas', opciones.soloActivas ?? true)
      .set('soloBajoMinimo', opciones.soloBajoMinimo ?? false);
    if (texto.trim()) params = params.set('texto', texto.trim());

    return this.http.get<Pagina<Pieza>>(this.basePiezas, { params });
  }

  obtenerPieza(id: number): Observable<Pieza> {
    return this.http.get<Pieza>(`${this.basePiezas}/${id}`);
  }

  crearPieza(datos: Partial<Pieza> & { stockInicial?: number }): Observable<Pieza> {
    return this.http.post<Pieza>(this.basePiezas, datos, { params: this.conUsuario() });
  }

  /** Piezas que han caído al mínimo o por debajo. */
  alertas(): Observable<AlertaStock[]> {
    return this.http.get<AlertaStock[]>(`${this.baseInventario}/alertas`);
  }

  movimientos(piezaId?: number, tamano = 50): Observable<Pagina<MovimientoStock>> {
    let params = new HttpParams().set('size', tamano);
    if (piezaId) params = params.set('piezaId', piezaId);
    return this.http.get<Pagina<MovimientoStock>>(`${this.baseInventario}/movimientos`, { params });
  }

  movimientosDePieza(piezaId: number, tamano = 50): Observable<Pagina<MovimientoStock>> {
    return this.http.get<Pagina<MovimientoStock>>(
      `${this.baseInventario}/piezas/${piezaId}/movimientos`,
      { params: new HttpParams().set('size', tamano) },
    );
  }

  registrarEntrada(
    piezaId: number,
    datos: { cantidad: number; documentoProveedor?: string; precioCosteUnitario?: number; motivo?: string },
  ): Observable<MovimientoStock> {
    return this.http.post<MovimientoStock>(
      `${this.baseInventario}/piezas/${piezaId}/entradas`,
      datos,
      { params: this.conUsuario() },
    );
  }

  registrarSalida(
    piezaId: number,
    datos: { cantidad: number; motivo: string },
  ): Observable<MovimientoStock> {
    return this.http.post<MovimientoStock>(
      `${this.baseInventario}/piezas/${piezaId}/salidas`,
      datos,
      { params: this.conUsuario() },
    );
  }

  /** Ajuste de inventario. La cantidad lleva signo: negativa si faltan unidades. */
  registrarAjuste(
    piezaId: number,
    datos: { cantidad: number; motivo: string },
  ): Observable<MovimientoStock> {
    return this.http.post<MovimientoStock>(
      `${this.baseInventario}/piezas/${piezaId}/ajustes`,
      datos,
      { params: this.conUsuario() },
    );
  }

  proveedores(texto = ''): Observable<Pagina<Proveedor>> {
    let params = new HttpParams().set('size', 100);
    if (texto.trim()) params = params.set('texto', texto.trim());
    return this.http.get<Pagina<Proveedor>>(`${environment.urlApi}/proveedores`, { params });
  }

  private conUsuario(): HttpParams {
    const usuarioId = this.sesion.usuarioId();
    return usuarioId ? new HttpParams().set('usuarioId', usuarioId) : new HttpParams();
  }
}
