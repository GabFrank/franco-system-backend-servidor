import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Volcar {
    public static void main(String[] args) throws Exception {
        Path modelos = Paths.get(args[0]), dir = Paths.get(args[1]), out = Paths.get(args[2]);
        Files.createDirectories(out);
        File[] fs = dir.toFile().listFiles(f -> f.getName().endsWith(".jpg"));
        Arrays.sort(fs);
        try (MotorOcr m = new MotorOcr(modelos)) {
            m.reconocer(Imagen.leer(fs[0]));                      // calentamiento de la JVM
            List<Long> tiempos = new ArrayList<>();
            for (File f : fs) {
                Imagen img = Imagen.leer(f);
                long t0 = System.nanoTime();
                List<MotorOcr.Linea> r = m.reconocer(img);
                long ms = (System.nanoTime()-t0)/1_000_000;
                tiempos.add(ms);
                StringBuilder sb = new StringBuilder();
                for (MotorOcr.Linea l : r) sb.append(l.texto).append('\n');
                Files.writeString(out.resolve(f.getName().replace(".jpg",".txt")),
                                  sb.toString(), StandardCharsets.UTF_8);
                System.out.printf("  %-28s %5d ms  %2d lineas%n", f.getName(), ms, r.size());
            }
            Collections.sort(tiempos);
            System.out.printf("JAVA mediana %d ms  (min %d, max %d)%n",
                    tiempos.get(tiempos.size()/2), tiempos.get(0), tiempos.get(tiempos.size()-1));
        }
    }
}
