import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Componente raiz. La aplicacion real se construye en la fase 6; de momento
 * solo se comprueba que el andamiaje compila y se sirve correctamente.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly titulo = signal('MotorSport19 — Gestión del taller');
  protected readonly fase = signal('Fase 1: estructura, esquema de base de datos y entidades');
}
