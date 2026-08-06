import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { MotoResumen } from '../../nucleo/modelos/taller';
import { MotosService } from '../../nucleo/servicios/motos.service';

@Component({
  selector: 'app-lista-motos',
  imports: [CommonModule, FormsModule, RouterLink, Cargando, Icono],
  template: `
    <div class="pagina-cabecera">
      <div class="pagina-cabecera__texto">
        <h1>Motos</h1>
        <p class="pagina-cabecera__sub">
          {{ totalItems() }} {{ totalItems() === 1 ? 'moto registrada' : 'motos registradas' }}
        </p>
      </div>
    </div>

    <div class="filtros">
      <div class="campo filtros__crece">
        <label for="buscar">Buscar</label>
        <input
          id="buscar"
          type="search"
          placeholder="Matrícula, marca, modelo o bastidor…"
          [ngModel]="texto()"
          (ngModelChange)="texto.set($event)"
          (keyup.enter)="cargar()"
        />
      </div>
      <button type="button" class="boton" (click)="cargar()">
        <app-icono nombre="buscar" [tamano]="16" />
        Buscar
      </button>
    </div>

    <section class="tarjeta tarjeta--ajustada">
        @if (cargando()) {
          <app-cargando mensaje="Cargando motos…" />
        } @else if (!filas().length) {
          <div class="vacio">
            <app-icono class="vacio__icono" nombre="motos" [tamano]="30" />
            <span class="vacio__titulo">Ninguna moto encontrada</span>
            <span class="pequeno">Prueba con la matrícula, la marca o el bastidor.</span>
          </div>
        } @else {
          <div class="tabla-envoltorio">
            <table>
              <thead>
                <tr>
                  <th>Matrícula</th>
                  <th>Moto</th>
                  <th>Año</th>
                  <th class="num">Kilometraje</th>
                </tr>
              </thead>
              <tbody>
                @for (m of filas(); track m.id) {
                  <tr>
                    <td>
                      <div class="fila" style="gap: 6px; flex-wrap: nowrap">
                        <a [routerLink]="['/motos', m.id]" class="codigo">{{ m.matricula }}</a>
                        @if (!m.activo) {
                          <span class="etiqueta etiqueta--gris etiqueta--simple">De baja</span>
                        }
                      </div>
                    </td>
                    <td class="celda-doble__principal">{{ m.descripcion }}</td>
                    <td class="silenciado">{{ m.anio || '—' }}</td>
                    <td class="num importe">{{ m.kmActual | number: '1.0-0' : 'es' }} km</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          @if (totalPaginas() > 1) {
            <div class="paginacion">
              <span class="pequeno silenciado">Página {{ pagina() + 1 }} de {{ totalPaginas() }}</span>
              <div class="fila">
                <button
                  type="button"
                  class="boton boton--pequeno"
                  [disabled]="pagina() === 0"
                  (click)="cargar(pagina() - 1)"
                >
                  <app-icono nombre="flecha-izquierda" [tamano]="14" /> Anterior
                </button>
                <button
                  type="button"
                  class="boton boton--pequeno"
                  [disabled]="pagina() + 1 >= totalPaginas()"
                  (click)="cargar(pagina() + 1)"
                >
                  Siguiente <app-icono nombre="flecha-derecha" [tamano]="14" />
                </button>
              </div>
            </div>
          }
        }
    </section>
  `,
})
export class ListaMotos {
  private readonly servicio = inject(MotosService);

  protected readonly cargando = signal(true);
  protected readonly filas = signal<MotoResumen[]>([]);
  protected readonly totalItems = signal(0);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  protected readonly texto = signal('');

  constructor() {
    this.cargar();
  }

  protected cargar(pagina = 0): void {
    this.cargando.set(true);
    this.servicio.buscar(this.texto(), true, pagina).subscribe({
      next: (p) => {
        this.filas.set(p.contenido);
        this.totalItems.set(p.totalItems);
        this.pagina.set(p.pagina);
        this.totalPaginas.set(p.totalPaginas);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }
}
