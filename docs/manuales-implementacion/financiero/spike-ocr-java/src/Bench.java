import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Corre el detector PP-OCRv4 sobre EL MISMO tensor que uso Python,
 *  para que la diferencia medida sea de ONNX Runtime y de nada mas. */
public class Bench {

    static float[] leerF32(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        FloatBuffer fb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        float[] f = new float[fb.remaining()];
        fb.get(f);
        return f;
    }

    static long[] leerForma(Path p) throws IOException {
        String[] partes = Files.readString(p).trim().split(",");
        long[] s = new long[partes.length];
        for (int i = 0; i < partes.length; i++) s[i] = Long.parseLong(partes[i].trim());
        return s;
    }

    public static void main(String[] args) throws Exception {
        Path base = Paths.get(args.length > 0 ? args[0] : ".");
        int repeticiones = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        float[] tensor = leerF32(base.resolve("tensor_det.f32"));
        long[] forma   = leerForma(base.resolve("tensor_det.shape"));
        float[] refer  = leerF32(base.resolve("salida_det.f32"));

        System.out.printf("ORT Java %s | JVM %s | %d nucleos%n",
                OrtEnvironment.class.getPackage().getImplementationVersion(),
                System.getProperty("java.version"),
                Runtime.getRuntime().availableProcessors());
        System.out.printf("tensor %s  (%d floats)%n", Arrays.toString(forma), tensor.length);

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        // se deja el default de hilos, igual que en el lado Python

        long tCarga = System.nanoTime();
        OrtSession sess = env.createSession(
                base.resolve("modelos/ch_PP-OCRv4_det_infer.onnx").toString(), opts);
        System.out.printf("carga del modelo: %d ms%n", (System.nanoTime()-tCarga)/1_000_000);

        String entrada = sess.getInputNames().iterator().next();
        float[] salida = null;

        for (int i = 0; i < 3; i++) salida = correr(env, sess, entrada, tensor, forma);   // calentamiento

        double[] ms = new double[repeticiones];
        for (int i = 0; i < repeticiones; i++) {
            long t0 = System.nanoTime();
            salida = correr(env, sess, entrada, tensor, forma);
            ms[i] = (System.nanoTime()-t0)/1e6;
        }
        Arrays.sort(ms);
        System.out.printf("JAVA det: mediana %.1f ms   min %.1f   max %.1f%n",
                ms[repeticiones/2], ms[0], ms[repeticiones-1]);

        // ---- comparacion numerica contra la referencia de Python ----
        double suma = 0, maxAbs = 0, sumAbs = 0;
        double mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
        int distintos = 0;
        for (int i = 0; i < salida.length; i++) {
            suma += salida[i];
            mn = Math.min(mn, salida[i]); mx = Math.max(mx, salida[i]);
            double d = Math.abs(salida[i] - refer[i]);
            sumAbs += d; maxAbs = Math.max(maxAbs, d);
            if (d > 1e-6) distintos++;
        }
        System.out.printf("salida  n=%d  min=%.6f max=%.6f mean=%.8f suma=%.4f%n",
                salida.length, mn, mx, suma/salida.length, suma);
        System.out.printf("vs Python: dif max %.3e   dif media %.3e   valores con dif>1e-6: %d (%.4f%%)%n",
                maxAbs, sumAbs/salida.length, distintos, 100.0*distintos/salida.length);
        sess.close(); env.close();
    }

    static float[] correr(OrtEnvironment env, OrtSession sess, String entrada,
                          float[] datos, long[] forma) throws OrtException {
        try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(datos), forma);
             OrtSession.Result r = sess.run(Collections.singletonMap(entrada, t))) {
            float[][][][] o = (float[][][][]) r.get(0).getValue();
            int h = o[0][0].length, w = o[0][0][0].length;
            float[] plano = new float[h*w];
            for (int y = 0; y < h; y++) System.arraycopy(o[0][0][y], 0, plano, y*w, w);
            return plano;
        }
    }
}
