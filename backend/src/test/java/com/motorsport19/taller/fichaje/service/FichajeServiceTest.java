package com.motorsport19.taller.fichaje.service;

import com.motorsport19.taller.fichaje.domain.Fichaje;
import com.motorsport19.taller.fichaje.repository.FichajeRepository;
import com.motorsport19.taller.support.RolesDePrueba;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FichajeServiceTest {

    @Mock
    private FichajeRepository repositorio;

    @Mock
    private UsuarioRepository usuarios;

    @InjectMocks
    private FichajeService servicio;

    @Test
    @DisplayName("la exportacion lleva las horas que cuentan y cada cambio con lo que habia antes")
    void exportacion() {
        Usuario javier = Usuario.crear("jortega", "$2a$10$x", "Javier Ortega", null, null, RolesDePrueba.taller());
        Usuario jefe = Usuario.crear("admin", "$2a$10$x", "Direccion", null, null, RolesDePrueba.administracion());
        Instant entrada = LocalDateTime.of(2025, 3, 10, 8, 5).atZone(ZoneId.of("Europe/Madrid")).toInstant();

        Fichaje f = Fichaje.empezar(javier);
        ReflectionTestUtils.setField(f, "id", 7L);
        ReflectionTestUtils.setField(f, "inicio", entrada);
        f.cerrarPorOlvido(entrada.plus(Duration.ofMinutes(595)), "Se fue sin fichar", jefe);
        f.corregir(null, entrada.plus(Duration.ofMinutes(565)), "Se fue antes", jefe);

        when(repositorio.buscarTodas(any(), any(), any())).thenReturn(List.of(f));
        when(repositorio.cambiosDe(anyCollection())).thenReturn(f.getCambios());

        String[] lineas = new String(servicio.exportarCsv(
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 10), null), StandardCharsets.UTF_8).split("\n");

        assertThat(lineas).hasSize(2);
        assertThat(lineas[0]).isEqualTo("﻿trabajador;entrada;salida;duracion;horas;estado;cambios");
        assertThat(lineas[1])
                .startsWith("Javier Ortega;10/03/2025 08:05;10/03/2025 17:30;9:25:00;9,42;Cerrada a mano + Cambiada;")
                .contains("Direccion: 10/03 08:05 – sin salida → 10/03 08:05 – 18:00 (Se fue sin fichar) | ")
                .endsWith("Direccion: 10/03 08:05 – 18:00 → 10/03 08:05 – 17:30 (Se fue antes)");
    }
}
