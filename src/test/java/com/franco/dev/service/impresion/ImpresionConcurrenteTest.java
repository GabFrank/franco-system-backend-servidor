package com.franco.dev.service.impresion;

import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.service.impresion.dto.GastoDto;
import com.franco.dev.graphql.operaciones.VentaGraphQL;
import com.franco.dev.graphql.print.PrintGraphQL;
import com.franco.dev.print.operaciones.MovimientoPrintService;
import com.franco.dev.service.utils.ImageService;
import com.franco.dev.service.utils.PrintingService;
import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.output.CapturaPrintService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import javax.print.Doc;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * #533: dos impresiones simultaneas por el mismo bean no se cruzan. Antes el stream y la impresora
 * eran campos del singleton y la segunda pisaba a la primera.
 */
class ImpresionConcurrenteTest {

    private static final int VUELTAS = 30;

    /** Impresora falsa que guarda cada trabajo por separado. */
    private static PrintService impresora(String nombre, Queue<byte[]> trabajos) throws Exception {
        PrintService ps = mock(PrintService.class);
        when(ps.getName()).thenReturn(nombre);
        when(ps.createPrintJob()).thenAnswer(i -> {
            DocPrintJob job = mock(DocPrintJob.class);
            doAnswer(inv -> {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = ((Doc) inv.getArgument(0)).getStreamForBytes()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                trabajos.add(out.toByteArray());
                return null;
            }).when(job).print(any(), any());
            return job;
        });
        return ps;
    }

    /** Corre las dos impresiones VUELTAS veces cada una, a la vez, y devuelve los errores. */
    private static List<Throwable> enParalelo(Callable<?> a, Callable<?> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (Callable<?> c : Arrays.asList(a, b)) {
            futuros.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < VUELTAS; i++) c.call();
                return null;
            }));
        }
        largada.countDown();
        List<Throwable> errores = new ArrayList<>();
        for (Future<?> f : futuros) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                errores.add(e.getCause() != null ? e.getCause() : e);
            }
        }
        pool.shutdownNow();
        return errores;
    }

    private static void soloTicketsPropios(Queue<byte[]> trabajos, byte[] esperado, String impresora) {
        assertEquals(VUELTAS, trabajos.size(), "trabajos que llegaron a " + impresora);
        for (byte[] t : trabajos) {
            assertArrayEquals(esperado, t, "ticket mezclado o cortado en " + impresora);
        }
    }

    private static GastoDto gasto(String cajero) {
        GastoDto g = mock(GastoDto.class, RETURNS_DEEP_STUBS);
        when(g.getUsuario().getPersona().getNombre()).thenReturn(cajero);
        when(g.getResponsable().getPersona().getNombre()).thenReturn(cajero);
        when(g.getFecha()).thenReturn(LocalDateTime.of(2026, 11, 17, 10, 0));
        when(g.getAutorizadoPor()).thenReturn(null);
        when(g.getTipoGasto()).thenReturn(null);
        when(g.getObservacion()).thenReturn("gasto de " + cajero);
        return g;
    }

    @Test
    void dosGastosALaVezSalenCadaUnoEnSuImpresora() throws Exception {
        ImageService imageService = mock(ImageService.class);
        File dir = Files.createTempDirectory("frc-533").toFile();
        dir.deleteOnExit();
        when(imageService.getImagePath()).thenReturn(CapturaPrintService.directorioConLogo(dir));
        Queue<byte[]> enA = new ConcurrentLinkedQueue<>();
        Queue<byte[]> enB = new ConcurrentLinkedQueue<>();
        PrintingService printingService = mock(PrintingService.class);
        PrintService a = impresora("a", enA);
        PrintService b = impresora("b", enB);
        when(printingService.getPrintService("a")).thenReturn(a);
        when(printingService.getPrintService("b")).thenReturn(b);
        ImpresionService service = new ImpresionService();
        ReflectionTestUtils.setField(service, "imageService", imageService);
        ReflectionTestUtils.setField(service, "printingService", printingService);
        GastoDto ga = gasto("CAJERO A");
        GastoDto gb = gasto("CAJERO B");

        // referencia: cada ticket impreso solo
        service.printGasto(ga, "a", null);
        service.printGasto(gb, "b", null);
        byte[] ticketA = enA.poll();
        byte[] ticketB = enB.poll();
        assertNotNull(ticketA);
        assertNotNull(ticketB);
        assertFalse(Arrays.equals(ticketA, ticketB));

        List<Throwable> errores = enParalelo(
                () -> { service.printGasto(ga, "a", null); return null; },
                () -> { service.printGasto(gb, "b", null); return null; });

        assertTrue(errores.isEmpty(), "errores: " + errores);
        soloTicketsPropios(enA, ticketA, "a");
        soloTicketsPropios(enB, ticketB, "b");
    }

    private static String png(int rayas) throws Exception {
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 200; x += rayas) for (int y = 0; y < 100; y++) img.setRGB(x, y, 0xFFFFFF);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void dosTicketsDeCajaALaVezSalenCadaUnoEnSuImpresora() throws Exception {
        Queue<byte[]> enA = new ConcurrentLinkedQueue<>();
        Queue<byte[]> enB = new ConcurrentLinkedQueue<>();
        PrintingService service = BeanUtils.instantiateClass(PrintingService.class);
        @SuppressWarnings("unchecked")
        List<PrintService> cache = (List<PrintService>) ReflectionTestUtils.getField(service, "printServiceList");
        cache.add(impresora("a", enA));
        cache.add(impresora("b", enB));
        String imgA = png(3);
        String imgB = png(11);

        assertTrue(service.printTicketCaja58mm(imgA, "a"));
        assertTrue(service.printTicketCaja58mm(imgB, "b"));
        byte[] ticketA = enA.poll();
        byte[] ticketB = enB.poll();
        assertFalse(Arrays.equals(ticketA, ticketB));

        List<Throwable> errores = enParalelo(
                () -> { assertTrue(service.printTicketCaja58mm(imgA, "a")); return null; },
                () -> { assertTrue(service.printTicketCaja58mm(imgB, "b")); return null; });

        assertTrue(errores.isEmpty(), "errores: " + errores);
        soloTicketsPropios(enA, ticketA, "a");
        soloTicketsPropios(enB, ticketB, "b");
    }

    /** Red contra la regresion: ningun bean de impresion vuelve a guardar impresora o stream en un campo. */
    @Test
    void losBeansDeImpresionNoGuardanImpresoraNiStream() throws Exception {
        List<Class<?>> prohibidos = Arrays.asList(PrintService.class, java.io.OutputStream.class, EscPos.class);
        for (Class<?> bean : Arrays.asList(FacturaLegalGraphQL.class, VentaGraphQL.class, ImpresionService.class,
                PrintingService.class, MovimientoPrintService.class, PrintGraphQL.class)) {
            for (Field f : bean.getDeclaredFields()) {
                for (Class<?> p : prohibidos) {
                    assertFalse(p.isAssignableFrom(f.getType()), bean.getSimpleName() + "." + f.getName());
                }
            }
        }
        assertTrue(ReflectionTestUtils.getField(BeanUtils.instantiateClass(PrintingService.class), "printServiceList") instanceof CopyOnWriteArrayList);
    }
}
