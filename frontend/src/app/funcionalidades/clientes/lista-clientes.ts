import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { ClienteResumen } from '../../nucleo/modelos/taller';
import { ClientesService } from '../../nucleo/servicios/clientes.service';

@Component({
  selector: 'app-lista-clientes',
  imports: [CommonModule, FormsModule, RouterLink, Cargando, Icono],
  template: `
    <div class="pagina-cabecera">
      <div class="pagina-cabecera__texto">
        <h1>Clientes</h1>
        <p class="pagina-cabecera__sub">
          {{ totalItems() }} {{ totalItems() === 1 ? 'cliente' : 'clientes' }}
        </p>
      </div>
    </div>

    <div class="filtros">
      <div class="campo filtros__crece">
        <label for="buscar">Buscar</label>
        <input
          id="buscar"
          type="search"
          placeholder="Nombre, apellidos, NIF, teléfono o email…"
          [ngModel]="texto()"
          (ngModelChange)="texto.set($event)"
          (keyup.enter)="cargar()"
        />
      </div>
      <button type="button" class="boton" (click)="cargar()">
        <app-icono nombre="buscar" [tamano]="16" />
        Buscar
      </button>
      <label class="casilla">
        <input
          type="checkbox"
          [ngModel]="soloActivos()"
          (ngModelChange)="soloActivos.set($event); cargar()"
        />
        Ocultar los dados de baja
      </label>
    </div>

    <section class="tarjeta tarjeta--ajustada">
        @if (cargando()) {
          <app-cargando mensaje="Cargando clientes…" />
        } @else if (!filas().length) {
          <div class="vacio">
            <app-icono class="vacio__icono" nombre="clientes" [tamano]="30" />
            <span class="vacio__titulo">Ningún cliente encontrado</span>
            <span class="pequeno">Prueba con otro nombre, NIF o teléfono.</span>
          </div>
        } @else {
          <div class="tabla-envoltorio">
            <table>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Documento</th>
                  <th>Teléfono</th>
                  <th>Email</th>
                  <th>Facturable</th>
                </tr>
              </thead>
              <tbody>
                @for (c of filas(); track c.id) {
                  <tr>
                    <td>
                      <div class="fila" style="gap: 6px; flex-wrap: nowrap">
                        <a [routerLink]="['/clientes', c.id]" class="truncado" style="max-width: 210px">
                          {{ c.nombreCompleto }}
                        </a>
                        @if (!c.activo) {
                          <span class="etiqueta etiqueta--gris etiqueta--simple">De baja</span>
                        }
                      </div>
                    </td>
                    <td class="mono silenciado">{{ c.documento || '—' }}</td>
                    <td class="tabular">{{ c.telefono || '—' }}</td>
                    <td class="silenciado">
                      <span class="truncado" style="max-width: 240px">{{ c.email || '—' }}</span>
                    </td>
                    <td>
                      @if (c.facturable) {
                        <span class="etiqueta etiqueta--verde">Sí</span>
                      } @else {
                        <span class="etiqueta etiqueta--ambar" title="Faltan datos fiscales">
                          Faltan datos
                        </span>
                      }
                    </td>
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
export class ListaClientes {
  private readonly servicio = inject(ClientesService);

  protected readonly cargando = signal(true);
  protected readonly filas = signal<ClienteResumen[]>([]);
  protected readonly totalItems = signal(0);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  protected readonly texto = signal('');
  protected readonly soloActivos = signal(true);

  constructor() {
    this.cargar();
  }

  protected cargar(pagina = 0): void {
    this.cargando.set(true);
    this.servicio.buscar(this.texto(), this.soloActivos(), pagina).subscribe({
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
