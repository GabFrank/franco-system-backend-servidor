package com.franco.dev.utilitarios.print.output;

import org.junit.jupiter.api.Test;

import javax.print.Doc;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Un fallo al mandar el trabajo a la impresora termina con error en vez de colgar al que escribe (issue #205). */
class PrinterOutputStreamTest {

    private static final Duration LIMITE = Duration.ofSeconds(5);
    /** Mas que el buffer de 1024 bytes del pipe: con el codigo anterior, escribir esto se colgaba. */
    private static final byte[] TICKET = new byte[5000];

    private static PrintService impresoraCon(DocPrintJob job) {
        PrintService ps = mock(PrintService.class);
        when(ps.createPrintJob()).thenReturn(job);
        return ps;
    }

    @Test
    void siElEnvioFallaLaEscrituraTerminaConElMotivo() throws Exception {
        DocPrintJob job = mock(DocPrintJob.class);
        doThrow(new PrintException("Printer is not accepting job.")).when(job).print(any(), any());
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));
        salida.setUncaughtException((t, e) -> { });

        IOException e = assertTimeoutPreemptively(LIMITE,
                () -> assertThrows(IOException.class, () -> salida.write(TICKET)));

        assertEquals("No se pudo imprimir: Printer is not accepting job.", e.getMessage());
    }

    @Test
    void siElEnvioFallaMientrasElQueEscribeEsperaLugarTambienTermina() throws Exception {
        DocPrintJob job = mock(DocPrintJob.class);
        // Falla tarde: para entonces el que escribe ya lleno el buffer y espera lugar.
        doAnswer(inv -> {
            Thread.sleep(300);
            throw new PrintException("Printer is not accepting job.");
        }).when(job).print(any(), any());
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));
        salida.setUncaughtException((t, e) -> { });

        IOException e = assertTimeoutPreemptively(LIMITE,
                () -> assertThrows(IOException.class, () -> salida.write(TICKET)));

        assertEquals("No se pudo imprimir: Printer is not accepting job.", e.getMessage());
    }

    @Test
    void siLaImpresoraVuelveSinLeerLaEscrituraTerminaConError() throws Exception {
        DocPrintJob job = mock(DocPrintJob.class);
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));

        IOException e = assertTimeoutPreemptively(LIMITE,
                () -> assertThrows(IOException.class, () -> salida.write(TICKET)));

        assertTrue(e.getMessage().startsWith("No se pudo imprimir"), e.getMessage());
    }

    @Test
    void unEnvioNormalRecibeTodoElTicket() throws Exception {
        DocPrintJob job = mock(DocPrintJob.class);
        AtomicInteger recibidos = new AtomicInteger();
        doAnswer(inv -> {
            InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes();
            byte[] buffer = new byte[512];
            int n;
            while ((n = in.read(buffer)) != -1) {
                recibidos.addAndGet(n);
            }
            return null;
        }).when(job).print(any(), any());
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));

        assertTimeoutPreemptively(LIMITE, () -> {
            salida.write(TICKET);
            salida.close();
            salida.threadPrint.join(LIMITE.toMillis());
        });

        assertEquals(TICKET.length, recibidos.get());
    }

    /** Un job que lee todo el ticket y despues falla: el ticket chico entra en el buffer sin que write se entere (#534). */
    private static DocPrintJob jobQueLeeYFalla() throws PrintException {
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes();
            while (in.read() != -1) {
                // consume hasta el cierre
            }
            throw new PrintException("Printer is not accepting job.");
        }).when(job).print(any(), any());
        return job;
    }

    @Test
    void unTicketChicoConElEnvioFalladoAvisaAlCerrar() throws Exception {
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(jobQueLeeYFalla()));
        salida.setUncaughtException((t, e) -> { });

        IOException e = assertTimeoutPreemptively(LIMITE, () -> {
            salida.write(new byte[100]);
            return assertThrows(IOException.class, salida::close);
        });

        assertEquals("No se pudo imprimir: Printer is not accepting job.", e.getMessage());
    }

    @Test
    void elSegundoCierreNoRepiteLaFalla() throws Exception {
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(jobQueLeeYFalla()));
        salida.setUncaughtException((t, e) -> { });

        assertTimeoutPreemptively(LIMITE, () -> {
            salida.write(new byte[100]);
            assertThrows(IOException.class, salida::close);
            salida.close();
        });
    }

    @Test
    void siElEnvioNoTerminaElCierreAvisaAlVencerLaEspera() throws Exception {
        CountDownLatch liberar = new CountDownLatch(1);
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            liberar.await();
            return null;
        }).when(job).print(any(), any());
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));
        salida.esperaEnvioMillis = 300;
        try {
            IOException e = assertTimeoutPreemptively(LIMITE, () -> {
                salida.write(new byte[100]);
                return assertThrows(IOException.class, salida::close);
            });

            assertTrue(e.getMessage().startsWith("No se pudo confirmar la impresion"), e.getMessage());
        } finally {
            liberar.countDown();
        }
    }

    @Test
    void unEnvioNormalTerminaAntesDeQueVuelvaElCierre() throws Exception {
        AtomicBoolean enviado = new AtomicBoolean();
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes();
            while (in.read() != -1) {
                // consume hasta el cierre
            }
            Thread.sleep(200);
            enviado.set(true);
            return null;
        }).when(job).print(any(), any());
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(job));

        assertTimeoutPreemptively(LIMITE, () -> {
            salida.write(new byte[100]);
            salida.close();
        });

        assertTrue(enviado.get());
    }

    /** Un job como el de CUPS: lee todo; si la lectura falla, el envio falla (no imprime). */
    private static DocPrintJob jobQueLee(AtomicInteger recibidos, long pausaPorBloqueMillis) throws PrintException {
        DocPrintJob job = mock(DocPrintJob.class);
        doAnswer(inv -> {
            InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes();
            byte[] buffer = new byte[100];
            int n;
            try {
                while ((n = in.read(buffer)) != -1) {
                    recibidos.addAndGet(n);
                    Thread.sleep(pausaPorBloqueMillis);
                }
            } catch (IOException | InterruptedException e) {
                throw new PrintException(e.toString());
            }
            return null;
        }).when(job).print(any(), any());
        return job;
    }

    @Test
    void unTicketAMediasSinCerrarCancelaElEnvio() throws Exception {
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(jobQueLee(new AtomicInteger(), 0)));
        salida.setUncaughtException((t, e) -> { });
        salida.abandonoMillis = 300;

        salida.write(new byte[100]); // el armado falla aca y nadie cierra
        salida.threadPrint.join(LIMITE.toMillis());

        assertFalse(salida.threadPrint.isAlive());
        IOException e = assertThrows(IOException.class, salida::close);
        assertTrue(e.getMessage().startsWith("No se pudo imprimir: el ticket quedo a medio armar"), e.getMessage());
    }

    @Test
    void unaSalidaNuncaEscritaNiCerradaTambienSeCancela() throws Exception {
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(jobQueLee(new AtomicInteger(), 0)));
        salida.setUncaughtException((t, e) -> { });
        salida.abandonoMillis = 300;

        salida.threadPrint.join(LIMITE.toMillis());

        assertFalse(salida.threadPrint.isAlive());
    }

    @Test
    void unaEscrituraQueEsperaAUnaImpresoraLentaNoSeCancela() throws Exception {
        AtomicInteger recibidos = new AtomicInteger();
        // 5000 bytes de a 100 cada 50 ms: el write queda bloqueado ~2,5 s, mucho mas que el plazo.
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(jobQueLee(recibidos, 50)));
        salida.abandonoMillis = 300;

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            salida.write(TICKET);
            salida.close();
        });

        assertEquals(TICKET.length, recibidos.get());
    }

    @Test
    void unaSalidaCerradaATiempoNoSeCancelaDespues() throws Exception {
        AtomicInteger recibidos = new AtomicInteger();
        PrinterOutputStream salida = new PrinterOutputStream(impresoraCon(jobQueLee(recibidos, 0)));
        salida.abandonoMillis = 300;

        salida.write(new byte[100]);
        salida.close();
        Thread.sleep(1500); // pasa el plazo y al menos una revision del vigia

        salida.close();
        assertEquals(100, recibidos.get());
    }
}
