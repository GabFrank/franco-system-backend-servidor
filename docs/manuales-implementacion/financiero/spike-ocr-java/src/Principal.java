import java.io.File;
import java.nio.file.Paths;
import java.util.List;

public class Principal {
    public static void main(String[] args) throws Exception {
        String modelos = args[0];
        try (MotorOcr m = new MotorOcr(Paths.get(modelos))) {
            for (int i = 1; i < args.length; i++) {
                File f = new File(args[i]);
                Imagen img = Imagen.leer(f);
                m.msDet = m.msCls = m.msRec = m.msPre = m.msPost = 0;
                long t0 = System.nanoTime();
                List<MotorOcr.Linea> r = m.reconocer(img);
                long ms = (System.nanoTime()-t0)/1_000_000;
                System.out.printf("%n===== %s  (%dx%d)  —  %d ms  —  %d lineas =====%n",
                        f.getName(), img.ancho, img.alto, ms, r.size());
                System.out.printf("      pre %d · det %d · post %d · cls %d · rec %d ms%n",
                        m.msPre, m.msDet, m.msPost, m.msCls, m.msRec);
                for (MotorOcr.Linea l : r) System.out.printf("  %.2f  %s%n", l.confianza, l.texto);
            }
        }
    }
}
