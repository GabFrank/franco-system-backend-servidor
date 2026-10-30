package com.franco.dev.service.impresion;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.output.CapturaPrintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Impresion desde el cliente: el balance de cierre escrito en memoria tiene que ser, byte por byte,
 * el mismo que hoy le llega a la impresora del central, y sin buscar ninguna impresora.
 */
class ImpresionServiceBalanceDestinoTest {

    @Mock private ImageService imageService;
    @Mock private PrintingService printingService;
    @InjectMocks private ImpresionService service;

    private CapturaPrintService impresora;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        File dir = Files.createTempDirectory("frc-ticket").toFile();
        dir.deleteOnExit();
        when(imageService.getImagePath()).thenReturn(CapturaPrintService.directorioConLogo(dir));
        impresora = new CapturaPrintService();
        when(printingService.getPrintService(any())).thenReturn(impresora);
    }

    private static PdvCajaBalanceDto balance() {
        Persona p = new Persona();
        p.setNombre("CAJERO DE PRUEBA CON UN NOMBRE MUY LARGO");
        Usuario u = new Usuario();
        u.setPersona(p);
        PdvCajaBalanceDto dto = new PdvCajaBalanceDto();
        dto.setIdCaja(7166L);
        dto.setUsuario(u);
        dto.setFechaApertura(LocalDateTime.of(2026, 9, 6, 17, 12));
        dto.setFechaCierre(LocalDateTime.of(2026, 9, 7, 0, 30));
        dto.setTotalGsAper(1771500.0);
        dto.setTotalGsCierre(1670500.0);
        dto.setTotalVentaGs(1500000.0);
        return dto;
    }

    @Test
    void balanceEnMemoriaIgualAlImpreso() throws Exception {
        PdvCajaBalanceDto dto = balance();
        assertTrue(service.printBalance(dto, "ticket", "CAJA 1"));
        byte[] impreso = impresora.bytes();

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        reset(printingService);
        assertTrue(service.printBalance(dto, "ticket", "CAJA 1", memoria));

        assertTrue(impreso.length > 0);
        assertArrayEquals(impreso, memoria.toByteArray());
        verifyNoInteractions(printingService);
    }

    @Test
    void sinImpresoraElCentralNoImprimeYElClienteIgualRecibeElBalance() throws Exception {
        when(printingService.getPrintService(any())).thenReturn(null);
        assertFalse(service.printBalance(balance(), "no-existe", null));

        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        assertTrue(service.printBalance(balance(), null, null, memoria));
        assertTrue(memoria.size() > 0);
    }

    @Test
    void elBalanceDeUnaCajaAbiertaSaleConCierreAbierta() throws Exception {
        PdvCajaBalanceDto abierta = balance();
        abierta.setFechaCierre(null);
        abierta.setTotalTarjeta(null);
        abierta.setTotalRetiroRs(null);
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();

        assertTrue(service.printBalance(abierta, "ticket", "CAJA 1", memoria));
        assertTrue(new String(memoria.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1).contains("ABIERTA"));
    }

    @Test
    void unGastoYUnRetiroConMontosNulosSeImprimenConCero() throws Exception {
        Persona p = new Persona();
        p.setNombre("CAJERO");
        Usuario u = new Usuario();
        u.setPersona(p);
        com.franco.dev.domain.personas.Funcionario f = new com.franco.dev.domain.personas.Funcionario();
        f.setPersona(p);
        com.franco.dev.service.impresion.dto.GastoDto gasto = new com.franco.dev.service.impresion.dto.GastoDto();
        gasto.setId(15L);
        gasto.setCajaId(662L);
        gasto.setFecha(LocalDateTime.of(2026, 10, 30, 10, 0));
        gasto.setUsuario(u);
        gasto.setResponsable(f);
        gasto.setAutorizadoPor(f);
        com.franco.dev.domain.financiero.TipoGasto tipo = new com.franco.dev.domain.financiero.TipoGasto();
        tipo.setDescripcion("LIMPIEZA");
        gasto.setTipoGasto(tipo);

        service.printGasto(gasto, "ticket", "CAJA 1");
        String impreso = new String(impresora.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(impreso.contains("0"), impreso);
        assertFalse(impreso.contains("null"), impreso);

        com.franco.dev.service.impresion.dto.RetiroDto retiro = new com.franco.dev.service.impresion.dto.RetiroDto();
        retiro.setId(9L);
        retiro.setCajaId(662L);
        retiro.setFecha(LocalDateTime.of(2026, 10, 30, 10, 0));
        retiro.setUsuario(u);
        retiro.setResponsable(f);
        assertDoesNotThrow(() -> service.printRetiro(retiro, "ticket", "CAJA 1", false));
    }
}
