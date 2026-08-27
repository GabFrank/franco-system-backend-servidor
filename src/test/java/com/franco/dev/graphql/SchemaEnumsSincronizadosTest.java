package com.franco.dev.graphql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Un enum de Java que gana un valor y no lo gana su .graphqls revienta recien en runtime:
 * graphql-java no puede serializar el valor desconocido, loguea un WARN y devuelve null en
 * ese campo. La pantalla se rompe sin que falle ni el build ni el CI.
 *
 * Paso de verdad: el enum de Java. El schema tiene que declarar exactamente sus valores.
 *
 * Historial: EstadoPreGasto.PAGADO (V197.5) tumbo la lista de caja chica de la mobile-pwa.
 */
class SchemaEnumsSincronizadosTest {

    private static final Pattern ENUM_GRAPHQL = Pattern.compile("\\benum\\s+(\\w+)\\s*\\{([^}]*)\\}");
    private static final Pattern ENUM_JAVA = Pattern.compile("\\benum\\s+(\\w+)\\s*(?:implements[^{]*)?\\{");
    private static final Pattern VALOR = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    @Test
    void cadaEnumDelSchemaDeclaraLosMismosValoresQueSuEnumJava() {
        Map<String, EnumDeclarado> schema = enumsDelSchema();
        Map<String, List<Set<String>>> java = enumsDeJava();

        List<String> problemas = new ArrayList<>();
        for (Map.Entry<String, EnumDeclarado> e : schema.entrySet()) {
            List<Set<String>> candidatos = java.get(e.getKey());
            // Un enum declarado solo en el schema (sin contraparte Java) no tiene con que
            // desincronizarse. Con nombre duplicado en Java alcanza con que uno coincida.
            if (candidatos == null || candidatos.stream().anyMatch(c -> c.equals(e.getValue().valores))) {
                continue;
            }
            Set<String> deJava = candidatos.get(0);
            Set<String> falta = new TreeSet<>(deJava);
            falta.removeAll(e.getValue().valores);
            Set<String> sobra = new TreeSet<>(e.getValue().valores);
            sobra.removeAll(deJava);
            StringBuilder sb = new StringBuilder("  " + e.getKey() + " (" + e.getValue().archivo + ")");
            if (!falta.isEmpty()) {
                sb.append("\n      falta en el schema (Java lo tiene, revienta al serializar): ").append(falta);
            }
            if (!sobra.isEmpty()) {
                sb.append("\n      sobra en el schema (Java no lo tiene, revienta al recibirlo): ").append(sobra);
            }
            problemas.add(sb.toString());
        }

        if (!problemas.isEmpty()) {
            fail("Enums del schema desincronizados con los de Java:\n" + String.join("\n", problemas));
        }
    }

    private static class EnumDeclarado {
        final Set<String> valores;
        final Path archivo;

        EnumDeclarado(Set<String> valores, Path archivo) {
            this.valores = valores;
            this.archivo = archivo;
        }
    }

    private Map<String, EnumDeclarado> enumsDelSchema() {
        Map<String, EnumDeclarado> res = new HashMap<>();
        for (Path f : archivos(Paths.get("src", "main", "resources", "graphql"), ".graphqls")) {
            String txt = sinComentariosGraphql(leer(f));
            Matcher m = ENUM_GRAPHQL.matcher(txt);
            while (m.find()) {
                res.put(m.group(1), new EnumDeclarado(valores(m.group(2)), f));
            }
        }
        return res;
    }

    private Map<String, List<Set<String>>> enumsDeJava() {
        Map<String, List<Set<String>>> res = new HashMap<>();
        for (Path f : archivos(Paths.get("src", "main", "java"), ".java")) {
            String txt = sinComentariosJava(leer(f));
            Matcher m = ENUM_JAVA.matcher(txt);
            while (m.find()) {
                String cuerpo = cuerpoHastaLlaveDeCierre(txt, m.end() - 1);
                if (cuerpo == null) {
                    continue;
                }
                // Las constantes van antes del primer ';' (despues vienen campos y constructores).
                int fin = cuerpo.indexOf(';');
                if (fin != -1) {
                    cuerpo = cuerpo.substring(0, fin);
                }
                cuerpo = cuerpo.replaceAll("\\([^)]*\\)", "").replaceAll("@\\w+", "");
                Set<String> valores = new LinkedHashSet<>();
                for (String parte : cuerpo.split(",")) {
                    String v = parte.trim();
                    if (v.matches("[A-Z][A-Z0-9_]*")) {
                        valores.add(v);
                    }
                }
                if (!valores.isEmpty()) {
                    res.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(valores);
                }
            }
        }
        return res;
    }

    /** Devuelve el contenido entre la llave en {@code aperturaLlave} y la que la cierra. */
    private String cuerpoHastaLlaveDeCierre(String txt, int aperturaLlave) {
        int profundidad = 0;
        for (int i = aperturaLlave; i < txt.length(); i++) {
            char c = txt.charAt(i);
            if (c == '{') {
                profundidad++;
            } else if (c == '}') {
                profundidad--;
                if (profundidad == 0) {
                    return txt.substring(aperturaLlave + 1, i);
                }
            }
        }
        return null;
    }

    private Set<String> valores(String cuerpo) {
        Set<String> res = new LinkedHashSet<>();
        Matcher m = VALOR.matcher(cuerpo);
        while (m.find()) {
            res.add(m.group());
        }
        return res;
    }

    private String sinComentariosGraphql(String t) {
        return t.replaceAll("(?s)\"\"\".*?\"\"\"", "").replaceAll("#.*", "");
    }

    private String sinComentariosJava(String t) {
        return t.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//.*", "");
    }

    private List<Path> archivos(Path raiz, String extension) {
        try (Stream<Path> s = Files.walk(raiz)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(extension))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String leer(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
