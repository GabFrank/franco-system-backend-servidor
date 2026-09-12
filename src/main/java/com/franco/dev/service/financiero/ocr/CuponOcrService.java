package com.franco.dev.service.financiero.ocr;

import ai.onnxruntime.OrtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lee un cupon de tarjeta desde una imagen.
 *
 * <p><b>Es EL MISMO motor que corre en el filial, y eso es el punto.</b> Aca no lee cupones de
 * ventas --eso pasa en la caja-- sino los cupones de MUESTRA con los que se configura un formato:
 * se sube una foto, el motor la lee, y de ahi sale el mapa de regiones que despues baja a las 24
 * filiales.
 *
 * <p>Por eso tiene que ser el mismo motor y la misma version. Un mapa derivado con OTRO motor
 * describiria cajas que el motor de la caja no produce, y el error se manifestaria lejos: en una
 * sucursal, con un campo que desaparece porque la region acota el reconocimiento al lugar
 * equivocado. Las dos versiones se mueven juntas o no se mueven (ver el pom).
 *
 * <p>PP-OCRv4 sobre ONNX Runtime, dentro de esta misma JVM: no hay servicio Python al lado ni
 * binarios que provisionar en el disco. Los modelos y el diccionario viajan como recursos del
 * classpath.
 *
 * <p>Una sola instancia para toda la aplicacion: crear las sesiones de ORT cuesta ~100 ms y son
 * seguras de reusar entre hilos. El motor no guarda estado entre lecturas.
 */
@Service
public class CuponOcrService {

    private static final Logger log = LoggerFactory.getLogger(CuponOcrService.class);

    private static final String BASE = "ocr/";
    private static final String DET  = BASE + "ch_PP-OCRv4_det_infer.onnx";
    private static final String CLS  = BASE + "ch_ppocr_mobile_v2.0_cls_infer.onnx";
    private static final String REC  = BASE + "ch_PP-OCRv4_rec_infer.onnx";
    private static final String DIC  = BASE + "ppocr_keys.txt";

    private volatile MotorOcr motor;
    /** Ya se intento cargar y fallo. Sin esto, cada derivacion reintentaria y volveria a fallar. */
    private volatile boolean fallo;

    /**
     * ⚠️ <b>PEREZOSO, al reves que en el filial, y es a proposito.</b>
     *
     * <p>En el filial el motor se carga al arrancar porque el cajero no puede esperar 2,5 s con un
     * cliente enfrente: ahi el OCR esta en el camino de un cobro.
     *
     * <p>Aca no. Derivar el mapa pasa cuando entra un modelo de aparato nuevo — unas pocas veces
     * por ano. Cargarlo al arranque dejaria las sesiones de ONNX ocupando memoria <b>permanente</b>
     * en el servidor que le responde a las 24 filiales, al desktop y a la PWA, para una funcion que
     * casi nunca corre. El costo de la primera derivacion son 2,5 s, y del otro lado hay un
     * administrador configurando, no una caja cobrando.
     *
     * <p>Despues de la primera vez queda cargado: las sesiones son seguras de reusar entre hilos y
     * el motor no guarda estado entre lecturas.
     */
    private MotorOcr motor() {
        MotorOcr m = motor;
        if (m != null) return m;
        synchronized (this) {
            if (motor != null) return motor;
            if (fallo) throw new IllegalStateException("el motor de OCR no esta disponible");
            cargar();
            if (motor == null) throw new IllegalStateException("el motor de OCR no esta disponible");
            return motor;
        }
    }

    private void cargar() {
        long t0 = System.nanoTime();
        try {
            motor = new MotorOcr(recurso(DET), recurso(CLS), recurso(REC), diccionario());
            log.info("OCR listo en {} ms", (System.nanoTime() - t0) / 1_000_000);
        } catch (Throwable e) {
            // ⚠️ Throwable y no Exception.
            //
            // El modo de falla mas probable es que la libreria nativa de ONNX no cargue
            // --arquitectura sin binario, glibc vieja, jar podado sin la plataforma-- y eso llega
            // como UnsatisfiedLinkError, que es un Error y NO una Exception: un `catch (Exception)`
            // lo deja pasar. Verificado en el filial el 2026-09-10, arrancando en macOS ARM, donde
            // el jar slim no trae nativo.
            //
            // Lo que evita que eso tumbe central es que la carga sea PEREZOSA: si corriera en un
            // @PostConstruct como en el filial, el Error subiria por el arranque y se llevaria todo
            // el contexto de Spring. Alla esa leccion costo que no arrancara UNA sucursal; aca
            // habria costado el HQ de las 24, por una funcion de administracion.
            //
            // Y este catch sigue haciendo falta igual: convierte el Error en un "no disponible"
            // legible, en vez de un 500 con stack trace en la pantalla del administrador.
            motor = null;
            fallo = true;
            log.error("OCR NO disponible — no se va a poder derivar el mapa de un formato nuevo, "
                    + "pero el resto de central funciona normal", e);
        }
    }

    /**
     * Si se puede contar con el motor.
     *
     * <p><b>No fuerza la carga.</b> Lo consulta la pantalla de formatos para avisar antes de
     * ofrecer el boton, y forzarla ahi significaria que cualquiera que abra esa pantalla mete las
     * sesiones de ONNX en la memoria de central. Mientras no se haya intentado, la respuesta
     * honesta es "deberia": el fallo real se conoce recien cuando se usa, y ahi el mensaje lo dice.
     */
    public boolean disponible() {
        return !fallo;
    }

    /**
     * @param jpeg la foto del cupon de muestra
     * @return las lineas leidas, en orden de lectura, con su confianza
     */
    public MotorOcr.Resultado leer(byte[] jpeg) throws OrtException, IOException {
        return leer(jpeg, null);
    }

    /**
     * Lee acotando el reconocimiento a las zonas del mapa, si el formato tiene uno.
     *
     * <p>Es la palanca de rendimiento: reconocer 6 cajas en vez de 26 baja {@code rec} de 3.841 a
     * ~900 ms. Con {@code zonas} en null se lee el cupon entero, que es lo que pasa cuando el
     * formato no tiene mapa todavia.
     */
    public MotorOcr.Resultado leer(byte[] jpeg, List<MotorOcr.Zona> zonas)
            throws OrtException, IOException {
        MotorOcr m = motor();
        try (InputStream in = new ByteArrayInputStream(jpeg)) {
            return m.reconocer(Imagen.leer(in), zonas);
        }
    }

    /** El tamano de la imagen, que la derivacion necesita para normalizar. */
    public int[] tamano(byte[] jpeg) throws IOException {
        try (InputStream in = new ByteArrayInputStream(jpeg)) {
            Imagen i = Imagen.leer(in);
            return new int[]{i.ancho, i.alto};
        }
    }

    private static byte[] recurso(String ruta) throws IOException {
        try (InputStream in = new ClassPathResource(ruta).getInputStream()) {
            return StreamUtils.copyToByteArray(in);
        }
    }

    /**
     * El diccionario NO se lee de la metadata del modelo aunque este ahi: el binding
     * Java de ORT la expone via JNI NewStringUTF, que usa Modified UTF-8 y no admite
     * secuencias de 4 bytes. El unico caracter fuera del BMP (U+231C9, linea 6137)
     * vuelve como 4 caracteres sueltos, corre todos los indices y rompe la
     * decodificacion en silencio — se manifiesta como "faltan los espacios".
     * MotorOcr verifica el largo contra la dimension de salida del modelo y aborta.
     */
    private static List<String> diccionario() throws IOException {
        try (InputStream in = new ClassPathResource(DIC).getInputStream()) {
            String txt = new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8);
            List<String> lineas = new ArrayList<>();
            Collections.addAll(lineas, txt.split("\n", -1));
            while (!lineas.isEmpty() && lineas.get(lineas.size() - 1).isEmpty())
                lineas.remove(lineas.size() - 1);
            return lineas;
        }
    }
}
