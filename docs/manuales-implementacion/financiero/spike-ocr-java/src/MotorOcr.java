import ai.onnxruntime.*;
import java.nio.FloatBuffer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * PP-OCRv4 completo sobre ONNX Runtime, en Java puro.
 * Sin OpenCV, sin dependencias nativas mas alla del jar de ORT — que ya trae
 * los binarios de linux-x64, win-x64 y arm.
 */
public final class MotorOcr implements AutoCloseable {

    public static final class Linea {
        public final String texto; public final float confianza; public final double[][] caja;
        Linea(String t, float c, double[][] b) { texto=t; confianza=c; caja=b; }
    }

    private static final int DET_LADO_MIN = 736;
    private static final double DET_UMBRAL = 0.3, DET_UMBRAL_CAJA = 0.5, DET_EXPANSION = 1.6;
    private static final int REC_ALTO = 48, REC_ANCHO_BASE = 320, LOTE = 6;
    private static final int CLS_ALTO = 48, CLS_ANCHO = 192;
    private static final double CLS_UMBRAL = 0.9, PUNTAJE_TEXTO = 0.5;

    private final OrtEnvironment env;
    private final OrtSession sDet, sCls, sRec;
    private final String eDet, eCls, eRec;
    private final String[] dic;
    public long msDet, msCls, msRec, msPre, msPost;

    public MotorOcr(Path modelos) throws OrtException {
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions o = new OrtSession.SessionOptions();
        sDet = env.createSession(modelos.resolve("ch_PP-OCRv4_det_infer.onnx").toString(), o);
        sCls = env.createSession(modelos.resolve("ch_ppocr_mobile_v2.0_cls_infer.onnx").toString(), o);
        sRec = env.createSession(modelos.resolve("ch_PP-OCRv4_rec_infer.onnx").toString(), o);
        eDet = sDet.getInputNames().iterator().next();
        eCls = sCls.getInputNames().iterator().next();
        eRec = sRec.getInputNames().iterator().next();

        // OJO: el diccionario NO se lee de la metadata del modelo.
        // El binding Java de ORT expone la metadata via JNI NewStringUTF, que usa
        // "Modified UTF-8" y no admite secuencias de 4 bytes: el unico caracter fuera
        // del BMP del diccionario (U+231C9, linea 6137) vuelve como 4 chars sueltos.
        // Eso corre todos los indices posteriores y rompe la decodificacion en silencio.
        // Por eso el diccionario viaja como recurso UTF-8 al lado del modelo.
        List<String> chars;
        try {
            chars = Files.readAllLines(modelos.resolve("ppocr_keys.txt"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new OrtException("no se pudo leer ppocr_keys.txt: " + e.getMessage());
        }
        int n = chars.size();
        dic = new String[n + 2];
        dic[0] = "";                                   // blank del CTC
        for (int i = 0; i < n; i++) dic[i + 1] = chars.get(i);
        dic[n + 1] = " ";

        int clases = (int) ((ai.onnxruntime.TensorInfo)
                sRec.getOutputInfo().values().iterator().next().getInfo()).getShape()[2];
        if (clases != dic.length)
            throw new OrtException("diccionario de " + dic.length +
                    " entradas contra un modelo de " + clases + " clases");
    }

    public List<Linea> reconocer(Imagen img) throws OrtException {
        long t0 = System.nanoTime();

        // ---- 1. detector: lado corto a 736 como minimo, dimensiones multiplo de 32 ----
        int h = img.alto, w = img.ancho;
        double razon = Math.min(h, w) < DET_LADO_MIN ? (double) DET_LADO_MIN / Math.min(h, w) : 1.0;
        int rh = Math.max(32, (int) (Math.round((h * razon) / 32.0) * 32));
        int rw = Math.max(32, (int) (Math.round((w * razon) / 32.0) * 32));
        Imagen esc = img.escalar(rw, rh);

        float[] entrada = new float[3 * rh * rw];
        for (int y = 0, i = 0; y < rh; y++)
            for (int x = 0; x < rw; x++, i++)
                for (int c = 0; c < 3; c++)
                    entrada[c*rh*rw + i] = (float) (esc.canal(x, y, c) / 127.5 - 1.0);
        msPre += (System.nanoTime()-t0)/1_000_000;

        long t1 = System.nanoTime();
        float[] prob = correr(sDet, eDet, entrada, new long[]{1,3,rh,rw}, rh*rw);
        msDet += (System.nanoTime()-t1)/1_000_000;

        long t2 = System.nanoTime();
        List<DetectorCajas.Caja> cajas = new DetectorCajas(
                DET_UMBRAL, DET_UMBRAL_CAJA, DET_EXPANSION, 1000, true)
                .cajas(prob, rw, rh, w, h);
        ordenarCajas(cajas);
        msPost += (System.nanoTime()-t2)/1_000_000;

        if (cajas.isEmpty()) return List.of();

        // ---- 2. recortar cada caja y enderezar ----
        List<Imagen> recortes = new ArrayList<>(cajas.size());
        for (DetectorCajas.Caja c : cajas) {
            Imagen r = img.recortarCuadrilatero(c.p);
            if ((double) r.alto / r.ancho >= 1.5) r = r.rotar90Anti();
            recortes.add(r);
        }

        // ---- 3. clasificador de angulo ----
        long t3 = System.nanoTime();
        for (int i = 0; i < recortes.size(); i += LOTE) {
            int fin = Math.min(recortes.size(), i + LOTE), n = fin - i;
            float[] lote = new float[n * 3 * CLS_ALTO * CLS_ANCHO];
            for (int k = 0; k < n; k++)
                normalizar(recortes.get(i+k), CLS_ALTO, CLS_ANCHO, lote, k*3*CLS_ALTO*CLS_ANCHO);
            float[] out = correr(sCls, eCls, lote, new long[]{n,3,CLS_ALTO,CLS_ANCHO}, n*2);
            for (int k = 0; k < n; k++)
                if (out[k*2+1] > out[k*2] && out[k*2+1] > CLS_UMBRAL)
                    recortes.set(i+k, recortes.get(i+k).rotar180());
        }
        msCls += (System.nanoTime()-t3)/1_000_000;

        // ---- 4. reconocimiento, en lotes ordenados por relacion de aspecto ----
        long t4 = System.nanoTime();
        Integer[] orden = new Integer[recortes.size()];
        for (int i = 0; i < orden.length; i++) orden[i] = i;
        Arrays.sort(orden, Comparator.comparingDouble(
                i -> (double) recortes.get(i).ancho / recortes.get(i).alto));

        String[] textos = new String[recortes.size()];
        float[] confs = new float[recortes.size()];

        for (int b = 0; b < orden.length; b += LOTE) {
            int fin = Math.min(orden.length, b + LOTE), n = fin - b;
            double maxRazon = (double) REC_ANCHO_BASE / REC_ALTO;
            for (int k = 0; k < n; k++) {
                Imagen r = recortes.get(orden[b+k]);
                maxRazon = Math.max(maxRazon, (double) r.ancho / r.alto);
            }
            int anchoLote = (int) (REC_ALTO * maxRazon);
            float[] lote = new float[n * 3 * REC_ALTO * anchoLote];
            for (int k = 0; k < n; k++)
                normalizar(recortes.get(orden[b+k]), REC_ALTO, anchoLote, lote, k*3*REC_ALTO*anchoLote);

            float[] salida; int pasos;
            try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(lote),
                        new long[]{n,3,REC_ALTO,anchoLote});
                 OrtSession.Result r = sRec.run(Collections.singletonMap(eRec, t))) {
                float[][][] o = (float[][][]) r.get(0).getValue();
                pasos = o[0].length;
                salida = new float[n * pasos * dic.length];
                for (int i2 = 0; i2 < n; i2++)
                    for (int p = 0; p < pasos; p++)
                        System.arraycopy(o[i2][p], 0, salida, (i2*pasos + p)*dic.length, dic.length);
            }
            for (int k = 0; k < n; k++) {
                float[] cf = new float[1];
                textos[orden[b+k]] = ctc(salida, k, pasos, cf);
                confs[orden[b+k]] = cf[0];
            }
        }
        msRec += (System.nanoTime()-t4)/1_000_000;

        List<Linea> res = new ArrayList<>();
        for (int i = 0; i < textos.length; i++)
            if (textos[i] != null && !textos[i].isEmpty() && confs[i] >= PUNTAJE_TEXTO)
                res.add(new Linea(textos[i], confs[i], cajas.get(i).p));
        return res;
    }

    /** Decodificacion CTC voraz: argmax, quitar repetidos consecutivos, quitar blank. */
    private String ctc(float[] sal, int muestra, int pasos, float[] confSalida) {
        StringBuilder sb = new StringBuilder();
        double suma = 0; int cuenta = 0, previo = -1;
        for (int p = 0; p < pasos; p++) {
            int base = (muestra*pasos + p) * dic.length, mejor = 0;
            float vMejor = sal[base];
            for (int c = 1; c < dic.length; c++)
                if (sal[base+c] > vMejor) { vMejor = sal[base+c]; mejor = c; }
            if (mejor != previo && mejor != 0) {
                sb.append(dic[mejor]); suma += vMejor; cuenta++;
            }
            previo = mejor;
        }
        confSalida[0] = cuenta == 0 ? 0f : (float) (suma / cuenta);
        return sb.toString();
    }

    /** Escala a alto fijo respetando la relacion, rellena a la derecha con ceros. */
    private static void normalizar(Imagen im, int alto, int ancho, float[] destino, int offset) {
        double razon = (double) im.ancho / im.alto;
        int aResize = Math.min(ancho, (int) Math.ceil(alto * razon));
        aResize = Math.max(1, aResize);
        Imagen r = im.escalar(aResize, alto);
        for (int c = 0; c < 3; c++)
            for (int y = 0; y < alto; y++)
                for (int x = 0; x < aResize; x++)
                    destino[offset + c*alto*ancho + y*ancho + x] =
                            (float) (r.canal(x, y, c) / 127.5 - 1.0);
    }

    private float[] correr(OrtSession s, String entrada, float[] datos, long[] forma, int nSalida)
            throws OrtException {
        try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(datos), forma);
             OrtSession.Result r = s.run(Collections.singletonMap(entrada, t))) {
            Object v = r.get(0).getValue();
            float[] out = new float[nSalida];
            aplanar(v, out, new int[]{0});
            return out;
        }
    }
    private static void aplanar(Object o, float[] destino, int[] pos) {
        if (o instanceof float[] f) {
            System.arraycopy(f, 0, destino, pos[0], f.length); pos[0] += f.length;
        } else for (Object hijo : (Object[]) o) aplanar(hijo, destino, pos);
    }

    /** Orden de lectura: por fila, con tolerancia vertical — igual que sorted_boxes. */
    private static void ordenarCajas(List<DetectorCajas.Caja> c) {
        c.sort((a, b) -> {
            double dy = a.p[0][1] - b.p[0][1];
            if (Math.abs(dy) < 10) return Double.compare(a.p[0][0], b.p[0][0]);
            return Double.compare(a.p[0][1], b.p[0][1]);
        });
    }

    @Override public void close() throws OrtException { sDet.close(); sCls.close(); sRec.close(); }
}
