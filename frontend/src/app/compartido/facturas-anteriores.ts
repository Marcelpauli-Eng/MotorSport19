import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../environments/environment';
import { PdfService } from '../nucleo/servicios/pdf.service';
import { SesionService } from '../nucleo/servicios/sesion.service';

interface FacturaAnterior {
  id: number;
  origen: string;
  numero: string;
  fecha: string;
  motoId: number | null;
  matricula: string | null;
  conceptos: string[];
  total: number;
}

/**
 * Facturas del programa anterior de un cliente o de una moto.
 *
 * <p>No son facturas de este programa: van aparte y con su PDF original, que es
 * el que vale. Si no hay ninguna no se pinta nada, que será lo normal en los
 * clientes que llegaron después del cambio de programa.
 */
@Component({
  selector: 'app-facturas-anteriores',
  imports: [CommonModule, RouterLink],
  template: `
    @if (facturas().length) {
      <section class="tarjeta">
        <div class="tarjeta__titulo">
          <h2>Facturas anteriores</h2>
          <span class="pequeno silenciado">
            {{ facturas().length }} de {{ facturas()[0].origen }}, el programa de antes
          </span>
        </div>
        <div class="tabla-envoltorio">
          <table>
            <thead>
              <tr>
                <th>Factura</th>
                <th>Fecha</th>
                @if (conMoto()) {
                  <th>Moto</th>
                }
                <th>Conceptos</th>
                <th class="num">Total</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (f of facturas(); track f.id) {
                <tr>
                  <td class="mono pequeno">{{ f.numero }}</td>
                  <td class="pequeno" style="white-space: nowrap">{{ f.fecha | date: 'dd/MM/yy' }}</td>
                  @if (conMoto()) {
                    <td class="pequeno" style="white-space: nowrap">
                      @if (f.motoId) {
                        <a [routerLink]="['/motos', f.motoId]">{{ f.matricula }}</a>
                      } @else {
                        —
                      }
                    </td>
                  }
                  <td class="pequeno silenciado">
                    <span class="truncado" style="max-width: 220px">{{ f.conceptos.join(' · ') }}</span>
                  </td>
                  <td class="num tabular" style="white-space: nowrap">{{ f.total | number: '1.2-2' : 'es' }} €</td>
                  <td>
                    <button type="button" class="boton boton--pequeno" (click)="abrir(f)">PDF</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
})
export class FacturasAnteriores {
  private readonly http = inject(HttpClient);
  private readonly pdf = inject(PdfService);

  /** De quién son: «clientes/7» o «motos/12». */
  readonly de = input.required<string>();
  /** En la ficha del cliente se dice de qué moto era cada una; en la de la moto sobra. */
  readonly conMoto = input(false);

  protected readonly facturas = signal<FacturaAnterior[]>([]);

  /** Llevan importes: quien no puede ver facturas tampoco ve estas. */
  private readonly puedeVer = inject(SesionService).tienePermiso('FACTURAS_VER');

  constructor() {
    effect(() => {
      const de = this.de();
      if (!this.puedeVer) return;
      untracked(() =>
        this.http
          .get<FacturaAnterior[]>(`${environment.urlApi}/${de}/facturas-anteriores`)
          .subscribe((f) => this.facturas.set(f)),
      );
    });
  }

  protected abrir(f: FacturaAnterior): void {
    this.pdf.abrir(
      `${environment.urlApi}/facturas-anteriores/${f.id}/pdf`,
      `factura-${f.numero.replace(/\W+/g, '-')}.pdf`,
    );
  }
}
