package com.franco.dev.service.productos;

import com.franco.dev.domain.media.ImagenMaster;
import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.repository.media.ImagenMasterRepository;
import com.franco.dev.service.productos.FotoProductoService.Tamano;
import com.franco.dev.service.utils.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Issue #263: cada pantalla pide el tamano que dibuja, la foto que vale es la ultima subida al
 * directorio de presentaciones, y leerla no escribe nada (ni archivos ni filas de imagen_master).
 */
class FotoProductoServiceTest {

    private static final long PRESENTACION = 7L;
    private static final long PRODUCTO = 70L;

    @TempDir
    Path home;

    private final ImagenMasterRepository imagenMaster = mock(ImagenMasterRepository.class);
    private final PresentacionService presentaciones = mock(PresentacionService.class);
    private final FotoProductoService service = new FotoProductoService();
    private ImageService imageService;

    @BeforeEach
    void setUp() {
        imageService = new ImageService();
        ReflectionTestUtils.setField(imageService, "isWindows", false);
        ReflectionTestUtils.setField(imageService, "env", new MockEnvironment().withProperty("homepath", home.toString()));
        ReflectionTestUtils.setField(service, "imageService", imageService);
        ReflectionTestUtils.setField(service, "imagenMasterRepository", imagenMaster);
        ReflectionTestUtils.setField(service, "presentacionService", presentaciones);

        Presentacion principal = new Presentacion();
        principal.setId(PRESENTACION);
        when(presentaciones.findByPrincipalAndProductoId(true, PRODUCTO)).thenReturn(principal);
        when(imagenMaster.findByTipoReferenciaAndReferenciaId(any(), any())).thenReturn(Collections.emptyList());
    }

    @Test
    void laMedianaViveJuntoALasPresentacionesBajoElHomepath() {
        assertEquals(home + "/FRC/resources/images/productos/presentaciones/medianas/",
                imageService.getImagePresentacionesMediana());
    }

    @Test
    void cadaTamanoLeeSuArchivo() throws Exception {
        original("original"); mediana("mediana"); miniatura("miniatura");

        assertEquals("miniatura", contenido(service.dePresentacion(PRESENTACION, Tamano.MINIATURA)));
        assertEquals("mediana", contenido(service.dePresentacion(PRESENTACION, Tamano.MEDIANA)));
        assertEquals("original", contenido(service.dePresentacion(PRESENTACION, Tamano.ORIGINAL)));
    }

    @Test
    void sinMiniaturaCaeALaMedianaYDespuesAlOriginal() throws Exception {
        original("original"); mediana("mediana");
        assertEquals("mediana", contenido(service.dePresentacion(PRESENTACION, Tamano.MINIATURA)));

        Files.delete(archivo(imageService.getImagePresentacionesMediana()));
        assertEquals("original", contenido(service.dePresentacion(PRESENTACION, Tamano.MINIATURA)));
        assertEquals("original", contenido(service.dePresentacion(PRESENTACION, Tamano.MEDIANA)));
    }

    @Test
    void elOriginalNuncaDevuelveUnaVersionReducida() throws Exception {
        mediana("mediana"); miniatura("miniatura");

        assertNull(service.dePresentacion(PRESENTACION, Tamano.ORIGINAL));
    }

    @Test
    void sinFotoDevuelveNullYNoUnRelleno() {
        assertNull(service.dePresentacion(PRESENTACION, Tamano.MINIATURA));
        assertNull(service.dePresentacion(PRESENTACION, Tamano.MEDIANA));
        assertNull(service.deProducto(PRODUCTO, Tamano.ORIGINAL));
        assertNull(service.dePresentacion(null, Tamano.MINIATURA));
        assertNull(service.deProducto(null, Tamano.MINIATURA));
    }

    @Test
    void elProductoUsaLaFotoDeSuPresentacionPrincipal() throws Exception {
        miniatura("miniatura");

        assertEquals("miniatura", contenido(service.deProducto(PRODUCTO, Tamano.MINIATURA)));
    }

    @Test
    void productoSinPresentacionPrincipalNiFotoPropiaDevuelveNull() {
        when(presentaciones.findByPrincipalAndProductoId(true, PRODUCTO)).thenReturn(null);

        assertNull(service.deProducto(PRODUCTO, Tamano.MINIATURA));
    }

    @Test
    void productoSinPresentacionPrincipalUsaSuFotoPropia() throws Exception {
        when(presentaciones.findByPrincipalAndProductoId(true, PRODUCTO)).thenReturn(null);
        File propia = escribir(home.resolve("copias/producto.jpg"), "propia");
        when(imagenMaster.findByTipoReferenciaAndReferenciaId(TipoReferencia.PRODUCTO, PRODUCTO))
                .thenReturn(Collections.singletonList(fila(propia.getPath(), true)));

        assertEquals("propia", contenido(service.deProducto(PRODUCTO, Tamano.MINIATURA)));
    }

    @Test
    void unaConsultaQueFallaNoRompeLaLectura() {
        when(presentaciones.findByPrincipalAndProductoId(true, PRODUCTO)).thenThrow(new IllegalStateException("dos principales"));

        assertNull(service.deProducto(PRODUCTO, Tamano.MINIATURA));
    }

    @Test
    void filaHuerfanaNoTapaLaFotoDelDirectorioDePresentaciones() throws Exception {
        original("original");
        filas(fila(home.resolve("no/existe/image-1.jpg").toString(), true));

        assertEquals("original", contenido(service.deProducto(PRODUCTO, Tamano.ORIGINAL)));
    }

    @Test
    void laFotoReemplazadaGanaALaCopiaViejaDeImagenMaster() throws Exception {
        File copiaVieja = escribir(home.resolve("copias/image-1.jpg"), "vieja");
        filas(fila(copiaVieja.getPath(), true));
        original("nueva");

        assertEquals("nueva", contenido(service.deProducto(PRODUCTO, Tamano.ORIGINAL)));
    }

    @Test
    void sinArchivoEnPresentacionesSeUsaLaCopiaDeImagenMasterQueExista() throws Exception {
        File copia = escribir(home.resolve("copias/image-2.jpg"), "copia");
        // Duplicadas y las dos marcadas principal, como estan hoy 211 presentaciones.
        filas(fila(home.resolve("no/existe.jpg").toString(), true), fila(copia.getPath(), true));

        assertEquals("copia", contenido(service.dePresentacion(PRESENTACION, Tamano.MINIATURA)));
    }

    @Test
    void vencimientoUsaLaFotoDeLaPresentacionVencidaYSiNoLaDelProducto() throws Exception {
        long vencida = 8L;
        miniatura("principal");
        assertEquals("principal", contenido(service.deVencimiento(vencida, PRODUCTO, Tamano.MINIATURA)));

        escribir(Path.of(imageService.getImagePresentacionesThumb(), vencida + ".jpg"), "vencida");
        assertEquals("vencida", contenido(service.deVencimiento(vencida, PRODUCTO, Tamano.MINIATURA)));
    }

    @Test
    void leerNoEscribeNiArchivosNiFilas() throws Exception {
        // El caso que antes migraba: foto en disco y ninguna fila en imagen_master.
        original("original");
        List<String> antes = archivos();

        service.deProducto(PRODUCTO, Tamano.MINIATURA);
        service.deProducto(PRODUCTO, Tamano.MEDIANA);
        service.deProducto(PRODUCTO, Tamano.ORIGINAL);
        service.deVencimiento(PRESENTACION, PRODUCTO, Tamano.MINIATURA);

        assertEquals(antes, archivos());
        // Con la foto en su directorio ni siquiera se consulta imagen_master.
        verifyNoInteractions(imagenMaster);
    }

    @Test
    void sinFotoSoloConsultaImagenMasterYNoEscribe() throws Exception {
        List<String> antes = archivos();

        assertNull(service.deProducto(PRODUCTO, Tamano.MINIATURA));

        assertEquals(antes, archivos());
        verify(imagenMaster).findByTipoReferenciaAndReferenciaId(TipoReferencia.PRESENTACION, PRESENTACION);
        verifyNoMoreInteractions(imagenMaster);
    }

    // ── apoyo ──

    private void original(String texto) throws Exception { escribir(archivo(imageService.getImagePresentaciones()), texto); }
    private void mediana(String texto) throws Exception { escribir(archivo(imageService.getImagePresentacionesMediana()), texto); }
    private void miniatura(String texto) throws Exception { escribir(archivo(imageService.getImagePresentacionesThumb()), texto); }

    private Path archivo(String directorio) {
        return Path.of(directorio, PRESENTACION + ".jpg");
    }

    private File escribir(Path destino, String texto) throws Exception {
        Files.createDirectories(destino.getParent());
        return Files.write(destino, texto.getBytes(StandardCharsets.UTF_8)).toFile();
    }

    private void filas(ImagenMaster... filas) {
        when(imagenMaster.findByTipoReferenciaAndReferenciaId(TipoReferencia.PRESENTACION, PRESENTACION))
                .thenReturn(Arrays.asList(filas));
    }

    private ImagenMaster fila(String url, boolean principal) {
        ImagenMaster fila = new ImagenMaster();
        fila.setUrl(url);
        fila.setPrincipal(principal);
        return fila;
    }

    private String contenido(String dataUri) {
        assertNotNull(dataUri, "se esperaba una foto y llego null");
        assertTrue(dataUri.startsWith("data:image/jpg;base64,"), dataUri);
        return new String(Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1)), StandardCharsets.UTF_8);
    }

    private List<String> archivos() throws Exception {
        try (Stream<Path> todos = Files.walk(home)) {
            return todos.map(Path::toString).sorted().collect(Collectors.toList());
        }
    }
}
