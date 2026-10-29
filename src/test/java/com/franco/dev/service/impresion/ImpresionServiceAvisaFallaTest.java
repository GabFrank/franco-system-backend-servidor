package com.franco.dev.service.impresion;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.output.CapturaPrintService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.print.Doc;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Una impresion que falla, o sin impresora, le llega al que imprime en vez de quedar en el log. */
class ImpresionServiceAvisaFallaTest {

    @Mock private ImageService imageService;
    @Mock private PrintingService printingService;
    @InjectMocks private ImpresionService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        File dir = Files.createTempDirectory("frc-ticket").toFile();
        dir.deleteOnExit();
        when(imageService.getImagePath()).thenReturn(CapturaPrintService.directorioConLogo(dir));
    }

    /** Como CUPS con una cola que no acepta: lee el trabajo entero y despues falla. */
    private static PrintService impresoraQueRechaza() throws PrintException {
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes();
            while (in.read() != -1) {
                // consume hasta el cierre
            }
            throw new PrintException("Printer is not accepting job.");
        }).when(job).print(any(), any());
        PrintService ps = mock(PrintService.class);
        when(ps.createPrintJob()).thenReturn(job);
        return ps;
    }

    @Test
    void sinImpresoraAvisa() {
        when(printingService.getPrintService("adesivo")).thenReturn(null);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.imprimirCodigoDeBarra(new Codigo()));

        assertEquals("No se encontro la impresora adesivo", e.getMessage());
    }

    @Test
    void siElEnvioFallaAvisaConElMotivo() throws Exception {
        PrintService rechaza = impresoraQueRechaza();
        when(printingService.getPrintService("adesivo")).thenReturn(rechaza);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.imprimirCodigoDeBarra(new Codigo()));

        assertEquals("No se pudo imprimir: Printer is not accepting job.", e.getMessage());
    }

    @Test
    void conImpresoraImprime() throws Exception {
        CapturaPrintService impresora = new CapturaPrintService();
        when(printingService.getPrintService("adesivo")).thenReturn(impresora);

        service.imprimirCodigoDeBarra(new Codigo());

        assertTrue(impresora.bytes().length > 0);
    }
}
