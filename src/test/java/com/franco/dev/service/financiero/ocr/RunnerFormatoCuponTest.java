package com.franco.dev.service.financiero.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.financiero.FormatoTerminalPos;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El taller para armar un formato de terminal POS a partir de fotos de tickets, sin la app.
 *
 * <p><b>Por que existe.</b> El patron de un formato se escribe contra lo que LEE el OCR, no contra
 * lo que ve una persona en la foto: PP-OCR confunde O con 0, pega o separa renglones, y el orden
 * en que devuelve las lineas no es el del papel. Hasta esta clase la unica forma de ver ese texto
 * era subir la foto por la app (captura en el filial o «Probar» en central), una por una. Con
 * esto el ciclo es: fotos -> texto medido -> patron y mapeo -> verde aca -> alta por ABM. Lo pidio
 * Gabriel el 2026-09-22 para que un agente pueda cerrar el ABM entero desde las fotos.
 *
 * <p><b>Como se usa.</b> Es un test de JUnit para no inventar otro punto de entrada, pero se
 * SALTEA si no le pasan la carpeta: el CI no lo corre nunca.
 * <pre>
 *   ./mvnw -q test -Dtest=RunnerFormatoCuponTest -Dsurefire.failIfNoSpecifiedTests=false \
 *       -DskipFlyway=true -Dcupon.dir=../cupones-prueba [-Dcupon.formato=infonet.json] [-Dcupon.salida=target/cupones]
 * </pre>
 * <ul>
 *   <li>{@code cupon.dir}: carpeta con las fotos (jpg/jpeg/png). Obligatoria.</li>
 *   <li>{@code cupon.formato}: JSON con {@code nombre}, {@code tipo}, {@code patron}, {@code mapeo}
 *       (objeto o string) y {@code ejemplo}. Sin el, solo imprime el texto OCR de cada foto: es el
 *       primer paso, antes de escribir el patron.</li>
 *   <li>{@code cupon.salida}: donde deja {@code <foto>.ocr.txt}, {@code <foto>.resultado.txt} y
 *       {@code resumen.md}. Por defecto {@code target/cupones}.</li>
 * </ul>
 *
 * <p><b>Que valida del formato antes de correr</b>, con las mismas reglas que el ABM
 * ({@code FormatoTerminalPosService.validar}) para que lo que pase aca no rebote al guardar: patron
 * anclado en {@code ^} y {@code $}, que compile, que reconozca su ejemplo dentro del plazo, y que
 * cada grupo que el mapeo nombra exista en el patron.
 *
 * <p>Usa el mismo motor que central ({@code CuponOcrService}: PP-OCRv4 det+cls+rec sobre ONNX,
 * modelos del classpath) y el mismo {@link ExtractorCupon}, asi que lo que sale aca es lo que va
 * a salir en la caja. En Mac el nativo de ONNX lo aporta el perfil {@code ocr-mac} del pom.
 */
class RunnerFormatoCuponTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void correr() throws Exception {
        String dir = System.getProperty("cupon.dir");
        Assumptions.assumeTrue(dir != null && !dir.trim().isEmpty(),
                "sin -Dcupon.dir no hay nada que leer; este test es una herramienta, no una verificacion");

        List<File> fotos = fotos(new File(dir));
        Assumptions.assumeTrue(!fotos.isEmpty(), "no hay jpg/jpeg/png en " + dir);

        Path salida = Paths.get(System.getProperty("cupon.salida", "target/cupones"));
        Files.createDirectories(salida);

        FormatoTerminalPos formato = null;
        Set<String> obligatorios = new LinkedHashSet<String>();
        String rutaFormato = System.getProperty("cupon.formato");
        if (rutaFormato != null && !rutaFormato.trim().isEmpty()) {
            formato = leerFormato(new File(rutaFormato));
            obligatorios = obligatorios(formato.getMapeo());
            validarComoElAbm(formato);
        }

        StringBuilder resumen = new StringBuilder();
        resumen.append("# Runner de formato de cupon\n\n");
        resumen.append("- carpeta: `").append(dir).append("` (").append(fotos.size()).append(" fotos)\n");
        resumen.append("- formato: ").append(formato == null ? "ninguno (solo lectura OCR)"
                : "`" + formato.getNombre() + "` (" + formato.getTipo() + ")").append("\n");
        if (!obligatorios.isEmpty()) resumen.append("- obligatorios: ").append(obligatorios).append("\n");
        resumen.append("\n| foto | ms | lineas | resultado | faltan obligatorios |\n|---|---|---|---|---|\n");

        int completos = 0;
        try (MotorOcr motor = motor()) {
            ExtractorCupon extractor = new ExtractorCupon();
            for (File f : fotos) {
                Imagen img = Imagen.leer(f);
                MotorOcr.Resultado ocr = motor.reconocer(img);
                String texto = ocr.textoPorRenglones();

                StringBuilder ocrTxt = new StringBuilder();
                ocrTxt.append("# ").append(f.getName()).append("  ").append(img.ancho).append("x").append(img.alto)
                        .append("  ").append(ocr.msTotal).append(" ms (det ").append(ocr.msDet)
                        .append(", rec ").append(ocr.msRec).append(")\n\n");
                ocrTxt.append("## Texto por renglones (lo que ve ExtractorCupon)\n\n").append(texto).append("\n\n");
                ocrTxt.append("## Lineas crudas (confianza, caja y=arriba)\n\n");
                for (MotorOcr.Linea l : ocr.lineas) {
                    ocrTxt.append(String.format(Locale.ROOT, "%.3f  y=%5.0f x=%5.0f  %s%n",
                            l.confianza, l.caja[0][1], l.caja[0][0], l.texto));
                }
                escribir(salida.resolve(f.getName() + ".ocr.txt"), ocrTxt.toString());
                System.out.println("\n===== " + f.getName() + " (" + ocr.msTotal + " ms, "
                        + ocr.lineas.size() + " lineas) =====\n" + texto);

                String estado = "solo lectura";
                String faltan = "";
                if (formato != null) {
                    ExtractorCupon.Resultado r = extractor.extraer(texto, formato);
                    StringBuilder res = new StringBuilder();
                    res.append("# ").append(f.getName()).append(" contra `").append(formato.getNombre()).append("`\n\n");
                    if (!r.ok()) {
                        estado = "FALLO";
                        res.append("FALLO: ").append(r.error).append("\n");
                    } else {
                        List<String> sinValor = new ArrayList<String>();
                        for (String o : obligatorios) if (!r.campos.containsKey(o)) sinValor.add(o);
                        estado = r.parcial ? "PARCIAL" : (sinValor.isEmpty() ? "COMPLETO" : "INCOMPLETO");
                        if (estado.equals("COMPLETO")) completos++;
                        faltan = String.join(", ", sinValor);
                        res.append("estado: ").append(estado).append(r.parcial ? " (el patron entero no matcheo; rescate por tramos)" : "").append("\n\n");
                        res.append("| campo | valor | confianza OCR | tramo |\n|---|---|---|---|\n");
                        for (Map.Entry<String, Object> e : r.campos.entrySet()) {
                            int[] rg = r.rangos.get(e.getKey());
                            Float conf = rg == null ? null : ocr.confianzaEnRango(rg[0], rg[1]);
                            res.append("| ").append(e.getKey()).append(" | `").append(e.getValue()).append("` | ")
                                    .append(conf == null ? "-" : String.format(Locale.ROOT, "%.3f", conf))
                                    .append(" | ").append(rg == null ? "-" : rg[0] + "-" + rg[1]).append(" |\n");
                        }
                        if (!r.extras.isEmpty()) res.append("\nextras (datos_extra): ").append(r.extras).append("\n");
                        if (!sinValor.isEmpty()) res.append("\nobligatorios sin valor: ").append(sinValor).append("\n");
                    }
                    escribir(salida.resolve(f.getName() + ".resultado.txt"), res.toString());
                    System.out.println("-> " + estado + (faltan.isEmpty() ? "" : " (faltan: " + faltan + ")")
                            + (r.ok() ? " " + r.campos : " " + r.error));
                }
                resumen.append("| ").append(f.getName()).append(" | ").append(ocr.msTotal).append(" | ")
                        .append(ocr.lineas.size()).append(" | ").append(estado).append(" | ").append(faltan).append(" |\n");
            }
        }
        if (formato != null) {
            resumen.append("\n**").append(completos).append(" de ").append(fotos.size())
                    .append(" fotos completas** (todos los obligatorios, sin rescate por tramos).\n");
        }
        escribir(salida.resolve("resumen.md"), resumen.toString());
        System.out.println("\n" + resumen);
        System.out.println("archivos en " + salida.toAbsolutePath());
    }

    // ---------------------------------------------------------------------------------------

    /** Mismos recursos y mismo diccionario que {@code CuponOcrService}; sin Spring. */
    private static MotorOcr motor() throws Exception {
        List<String> dic = new ArrayList<String>();
        String txt = new String(recurso("ocr/ppocr_keys.txt"), StandardCharsets.UTF_8);
        dic.addAll(Arrays.asList(txt.split("\n", -1)));
        while (!dic.isEmpty() && dic.get(dic.size() - 1).isEmpty()) dic.remove(dic.size() - 1);
        long t0 = System.nanoTime();
        MotorOcr m = new MotorOcr(recurso("ocr/ch_PP-OCRv4_det_infer.onnx"),
                recurso("ocr/ch_ppocr_mobile_v2.0_cls_infer.onnx"),
                recurso("ocr/ch_PP-OCRv4_rec_infer.onnx"), dic);
        System.out.println("motor OCR listo en " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        return m;
    }

    private static byte[] recurso(String ruta) throws IOException {
        try (InputStream in = RunnerFormatoCuponTest.class.getClassLoader().getResourceAsStream(ruta)) {
            if (in == null) throw new IOException("no esta en el classpath: " + ruta);
            return in.readAllBytes();
        }
    }

    private static List<File> fotos(File dir) {
        File[] todos = dir.listFiles();
        List<File> out = new ArrayList<File>();
        if (todos == null) return out;
        Arrays.sort(todos);
        for (File f : todos) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"))) out.add(f);
        }
        return out;
    }

    /** {@code mapeo} puede venir como objeto JSON (comodo de escribir) o como string (como en la base). */
    private static FormatoTerminalPos leerFormato(File f) throws IOException {
        JsonNode j = JSON.readTree(f);
        FormatoTerminalPos fo = new FormatoTerminalPos();
        fo.setNombre(j.path("nombre").asText(f.getName()));
        fo.setTipo(j.path("tipo").asText(FormatoTerminalPos.TIPO_MAQUINA));
        fo.setPatron(j.path("patron").asText(null));
        JsonNode m = j.get("mapeo");
        fo.setMapeo(m == null || m.isNull() ? null : (m.isTextual() ? m.asText() : m.toString()));
        fo.setEjemplo(j.path("ejemplo").asText(null));
        return fo;
    }

    private static Set<String> obligatorios(String mapeo) throws IOException {
        Set<String> out = new LinkedHashSet<String>();
        if (mapeo == null) return out;
        JsonNode root = JSON.readTree(mapeo);
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getValue().path("obligatorio").asBoolean(false)) out.add(e.getKey());
        }
        return out;
    }

    /** Las reglas de {@code FormatoTerminalPosService.validar} que se pueden chequear sin base. */
    private static void validarComoElAbm(FormatoTerminalPos fo) throws IOException {
        String p = fo.getPatron();
        if (p == null || p.trim().isEmpty()) throw new IllegalArgumentException("el formato no tiene patron");
        if (!p.startsWith("^") || !p.endsWith("$"))
            throw new IllegalArgumentException("el ABM lo va a rechazar: el patron debe empezar con ^ y terminar con $");
        Pattern pat = Pattern.compile(p, Pattern.DOTALL);
        if (fo.getEjemplo() != null && !fo.getEjemplo().trim().isEmpty()) {
            Matcher m = pat.matcher(ExtractorCupon.conPlazo(fo.getEjemplo()));
            if (!m.matches())
                throw new IllegalArgumentException("el ABM lo va a rechazar: el patron no reconoce su propio ejemplo");
        }
        if (fo.getMapeo() == null || fo.getMapeo().trim().isEmpty())
            throw new IllegalArgumentException("el ABM lo va a rechazar: el mapeo es obligatorio");
        JsonNode root = JSON.readTree(fo.getMapeo());
        if (!root.isObject()) throw new IllegalArgumentException("el mapeo debe ser un objeto JSON");
        Set<String> grupos = new LinkedHashSet<String>();
        Matcher g = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>").matcher(p);
        while (g.find()) grupos.add(g.group(1));
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        int vinculados = 0;
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            for (String clave : new String[]{"de", "deHora"}) {
                JsonNode ref = e.getValue().get(clave);
                if (ref == null || ref.isNull()) continue;
                if (!grupos.contains(ref.asText()))
                    throw new IllegalArgumentException("el ABM lo va a rechazar: el mapeo de \"" + e.getKey()
                            + "\" usa el grupo \"" + ref.asText() + "\" que el patron no declara. Grupos: " + grupos);
                vinculados++;
            }
            JsonNode tipo = e.getValue().get("tipo");
            if (tipo != null && !tipo.isNull()) {
                String t = tipo.asText().trim().toUpperCase(Locale.ROOT);
                if (!t.equals("TEXTO") && !t.equals("NUMERO") && !t.equals("FECHA"))
                    throw new IllegalArgumentException("el ABM lo va a rechazar: tipo de \"" + e.getKey()
                            + "\" = \"" + tipo.asText() + "\"; tiene que ser TEXTO, NUMERO o FECHA");
            }
        }
        if (vinculados == 0) throw new IllegalArgumentException("el ABM lo va a rechazar: el mapeo no vincula ningun campo con un grupo");
        System.out.println("formato `" + fo.getNombre() + "` pasa las validaciones del ABM; grupos: " + grupos);
    }

    private static void escribir(Path p, String contenido) throws IOException {
        Files.write(p, contenido.getBytes(StandardCharsets.UTF_8));
    }
}
