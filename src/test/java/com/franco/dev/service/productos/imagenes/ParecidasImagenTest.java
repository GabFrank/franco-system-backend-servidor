package com.franco.dev.service.productos.imagenes;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** «Buscar parecidas» siempre trae algo: Brave visual y, si Brave no responde, Bing con el título. */
class ParecidasImagenTest {

    private final BraveWebImagenes brave = mock(BraveWebImagenes.class);
    private final BingImagenes bing = mock(BingImagenes.class);
    private final SugerenciaImagenService service = new SugerenciaImagenService(null, null, null, bing, null, brave,
            new DescargaImagenService(1_000_000L), 500, 1000);

    private static ImagenSugerida img(String url, String titulo) {
        return new ImagenSugerida(url, url, 800, 800, titulo, null, null, ImagenSugerida.BING);
    }

    @Test
    void conSimilaresUsaLaBusquedaVisualDeBrave() throws Exception {
        when(brave.habilitada()).thenReturn(true);
        when(brave.parecidas("/api/images/similar?q=1&sig=2")).thenReturn(List.of(img("https://a.com/1.jpg", "Coca Cola")));
        ParecidasImagen r = service.parecidasDe("https://b.com/elegida.jpg", "Coca Cola 2L", "/api/images/similar?q=1&sig=2");
        assertEquals(1, r.getImagenes().size());
        assertFalse(r.isPorTexto());
        assertFalse(r.isAproximada());
        verifyNoInteractions(bing);
    }

    @Test
    void siBraveNoRespondeCaeEnBingConElTituloSinLaElegida() throws Exception {
        when(brave.habilitada()).thenReturn(true);
        when(brave.parecidas(anyString())).thenThrow(new IOException("HTTP 429"));
        when(bing.habilitada()).thenReturn(true);
        when(bing.buscar(eq("Coca Cola 2L caja x 4"), anyInt())).thenReturn(List.of(
                img("https://b.com/elegida.jpg", "Coca Cola 2L caja x 4"),
                img("https://c.com/caja.jpg", "Coca Cola 2L caja x 4 unidades"),
                img("https://d.com/lata.jpg", "Coca Cola lata")));
        ParecidasImagen r = service.parecidasDe("https://b.com/elegida.jpg", "Coca Cola 2L caja x 4", "/api/images/similar?q=1&sig=2");
        assertTrue(r.isPorTexto());
        assertEquals(List.of("https://c.com/caja.jpg", "https://d.com/lata.jpg"),
                r.getImagenes().stream().map(ImagenSugerida::getUrl).collect(java.util.stream.Collectors.toList()),
                "sin la foto elegida");
    }

    @Test
    void siBraveNoTraeNadaTambienCaeEnBing() throws Exception {
        when(brave.habilitada()).thenReturn(true);
        when(brave.imagenes(anyString())).thenReturn(List.of());
        when(bing.habilitada()).thenReturn(true);
        when(bing.buscar(anyString(), anyInt())).thenReturn(List.of(img("https://c.com/1.jpg", "x")));
        assertTrue(service.parecidasDe("https://b.com/e.jpg", "Don Franco IPA Hapee", null).isPorTexto());
    }

    @Test
    void soloFallaSiNingunoResponde() throws Exception {
        when(brave.habilitada()).thenReturn(true);
        when(brave.imagenes(anyString())).thenThrow(new IOException("HTTP 429"));
        when(bing.habilitada()).thenReturn(true);
        when(bing.buscar(anyString(), anyInt())).thenThrow(new IOException("timeout"));
        graphql.GraphQLException e = assertThrows(graphql.GraphQLException.class,
                () -> service.parecidasDe("https://b.com/e.jpg", "Coca Cola", null));
        assertTrue(e.getMessage().contains("ni Brave ni Bing"));
    }
}
