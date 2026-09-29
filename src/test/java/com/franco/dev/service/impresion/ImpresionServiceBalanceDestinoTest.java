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
}
