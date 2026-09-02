package com.franco.dev.graphql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Dos archivos que declaran el MISMO campo de Query o Mutation con firmas distintas se fusionan
 * en silencio: gana uno, el otro desaparece del schema y el arranque no dice nada. El cliente que
 * llama al perdedor recibe "Unknown field argument X" por cada argumento, como si la mutation
 * nunca se hubiera escrito.
 *
 * Historial: crearLote(productoId, numeroLote, ...) de inventario contra el crearLote() de SIFEN
 * —lote de documentos electronicos, sin argumentos—. La app no podia crear un lote durante el
 * conteo y el hallazgo costo una pasada entera de testeo manual contra alpha. Se renombro a
 * crearLoteProducto.
 *
 * Un duplicado con la firma IDENTICA no rompe nada: declara dos veces lo mismo. Se tolera, para
 * que el test hable solo del defecto que puede tumbar una pantalla.
 */
class SchemaSinCamposDuplicadosTest {

    private static final Pattern BLOQUE =
            Pattern.compile("(?:extend\\s+)?type\\s+(Query|Mutation|Subscription)\\s*\\{(.*?)\\n\\}", Pattern.DOTALL);
    private static final Pattern NOMBRE_CAMPO = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*[(:]");

    @Test
    void ningunCampoDeQueryOMutationSeDeclaraDosVecesConFirmasDistintas() {
        Map<String, List<Declaracion>> porCampo = camposDelSchema();

        List<String> problemas = new ArrayList<>();
        for (Map.Entry<String, List<Declaracion>> e : porCampo.entrySet()) {
            List<Declaracion> ds = e.getValue();
            if (ds.size() < 2) {
                continue;
            }
            boolean todasIguales = ds.stream().allMatch(d -> d.firma.equals(ds.get(0).firma));
            if (todasIguales) {
                continue;
            }
            StringBuilder sb = new StringBuilder("  " + e.getKey() + " declarado en:");
            for (Declaracion d : ds) {
                sb.append("\n      ").append(d.archivo).append(" -> ").append(d.firma);
            }
            problemas.add(sb.toString());
        }

        if (!problemas.isEmpty()) {
            fail("Campos de GraphQL duplicados con firmas distintas: gana uno y el otro se pierde "
                    + "sin error al arrancar. Renombra el nuevo con el sufijo de su dominio.\n"
                    + String.join("\n", problemas));
        }
    }

    private static class Declaracion {
        final String firma;
        final Path archivo;

        Declaracion(String firma, Path archivo) {
            this.firma = firma;
            this.archivo = archivo;
        }
    }

    /** Campos de primer nivel de cada bloque, con su firma completa (los argumentos van en varias lineas). */
    private Map<String, List<Declaracion>> camposDelSchema() {
        Map<String, List<Declaracion>> res = new LinkedHashMap<>();
        for (Path f : archivos(Paths.get("src", "main", "resources", "graphql"))) {
            String txt = leer(f);
            Matcher bloque = BLOQUE.matcher(txt);
            while (bloque.find()) {
                String tipo = bloque.group(1);
                for (Map.Entry<String, String> campo : camposDe(bloque.group(2)).entrySet()) {
                    res.computeIfAbsent(tipo + "." + campo.getKey(), k -> new ArrayList<>())
                            .add(new Declaracion(campo.getValue(), f));
                }
            }
        }
        return res;
    }

    private Map<String, String> camposDe(String cuerpo) {
        Map<String, String> res = new LinkedHashMap<>();
        int profundidad = 0;
        String nombreActual = null;
        StringBuilder firma = new StringBuilder();
        for (String linea : cuerpo.split("\n")) {
            String l = linea.trim();
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            if (profundidad == 0) {
                if (nombreActual != null) {
                    res.put(nombreActual, normalizar(firma.toString()));
                }
                Matcher m = NOMBRE_CAMPO.matcher(l);
                nombreActual = m.find() ? m.group(1) : null;
                firma.setLength(0);
            }
            firma.append(l).append(' ');
            profundidad += contar(l, '(') - contar(l, ')');
        }
        if (nombreActual != null) {
            res.put(nombreActual, normalizar(firma.toString()));
        }
        return res;
    }

    private String normalizar(String firma) {
        return firma.replaceAll("\\s+", " ").trim();
    }

    private int contar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    private List<Path> archivos(Path raiz) {
        try (Stream<Path> s = Files.walk(raiz)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".graphqls"))
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
