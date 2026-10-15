package com.franco.dev.service.productos.imagenes;

import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/** El central baja fotos por pedido del desktop: no puede servir de proxy abierto. */
class DescargaImagenServiceTest {

    @Test
    void soloBajaUrlsQueDevolvioUnaBusqueda() {
        DescargaImagenService s = new DescargaImagenService(1024);
        GraphQLException e = assertThrows(GraphQLException.class, () -> s.descargar("https://example.com/x.jpg"));
        assertTrue(e.getMessage().contains("búsqueda reciente"));
    }

    @Test
    void rechazaDireccionesInternas() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "172.25.1.200", "192.168.0.1", "169.254.169.254",
                "100.64.0.1", "0.0.0.0", "::1", "fd00::1", "fe80::1"}) {
            assertTrue(DescargaImagenService.esInterna(InetAddress.getByName(ip)), ip);
        }
        for (String ip : new String[]{"8.8.8.8", "181.120.10.1", "2606:4700::1111"}) {
            assertFalse(DescargaImagenService.esInterna(InetAddress.getByName(ip)), ip);
        }
        assertThrows(GraphQLException.class, () -> DescargaImagenService.validarDestino(URI.create("http://127.0.0.1:8081/graphql")));
        assertThrows(GraphQLException.class, () -> DescargaImagenService.validarDestino(URI.create("file:///etc/passwd")));
    }

    @Test
    void reconoceElTipoPorLaFirma() {
        assertEquals("image/jpeg", DescargaImagenService.tipoImagen(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertEquals("image/png", DescargaImagenService.tipoImagen(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)));
        assertEquals("image/webp", DescargaImagenService.tipoImagen(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')));
        assertNull(DescargaImagenService.tipoImagen("<html><body>403</body></html>".getBytes()));
    }

    @Test
    void cortaLaLecturaSiPasaElTope() throws Exception {
        assertEquals(10, DescargaImagenService.leerConTope(new ByteArrayInputStream(new byte[10]), 10).length);
        assertThrows(GraphQLException.class, () -> DescargaImagenService.leerConTope(new ByteArrayInputStream(new byte[11]), 10));
    }

    private static byte[] bytes(int... v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) b[i] = (byte) v[i];
        return b;
    }
}
