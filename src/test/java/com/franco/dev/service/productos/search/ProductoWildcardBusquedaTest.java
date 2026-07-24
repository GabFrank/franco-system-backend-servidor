package com.franco.dev.service.productos.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica sobre un indice Lucene real (in-memory) los predicados que arma
 * {@link ProductoSearchService}: prefijo, subcadena, subsecuencia, fuzzy y token
 * partido. Replica el analyzer de {@code ProductoAnalysisConfigurer}
 * (standard + lowercase + asciifolding) para que los terminos indexados sean los
 * mismos que en produccion.
 */
class ProductoWildcardBusquedaTest {

    private static final String CAMPO = "descripcion";

    private Directory directory;
    private Analyzer analyzer;

    @BeforeEach
    void setUp() throws IOException {
        analyzer = CustomAnalyzer.builder()
                .withTokenizer("standard")
                .addTokenFilter("lowercase")
                .addTokenFilter("asciifolding")
                .build();

        directory = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            indexar(writer, "CONTI BIER BOT 600ML");
            indexar(writer, "CONTI MALZBIER 350ML");
            indexar(writer, "CONTI GASEOSA UVA 2LT");
            indexar(writer, "PILSEN LATA 350ML");
            indexar(writer, "COCA COLA 2LT");
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        directory.close();
        analyzer.close();
    }

    // ---------- pasada estricta ----------

    @Test
    void prefijoSoloNoEncuentraCuandoFaltaLaPrimeraLetra() throws IOException {
        // Comportamiento viejo: unicamente wildcard de prefijo. Este era el bug.
        assertEquals(0, cantidadHits(prefijo("onti")));
    }

    @Test
    void subcadenaEncuentraCuandoFaltaLaPrimeraLetra() throws IOException {
        // "onti" -> CONTI: los 3 productos CONTI del indice.
        assertEquals(3, cantidadHits(subcadena("onti")));
    }

    @Test
    void prefijoSigueFuncionandoParaElTextoCompleto() throws IOException {
        assertEquals(3, cantidadHits(prefijo("conti")));
    }

    @Test
    void subcadenaNoTraeProductosAjenos() throws IOException {
        // La subcadena es match exacto: no inventa coincidencias.
        assertEquals(0, cantidadHits(subcadena("zzz")));
        assertEquals(1, cantidadHits(subcadena("ilsen")));
    }

    // ---------- pasada tolerante: subsecuencia ----------

    @Test
    void subsecuenciaEncuentraLetraFaltanteEnElMedio() throws IOException {
        // "cnti" no es subcadena de "conti", pero si subsecuencia.
        assertEquals(0, cantidadHits(subcadena("cnti")));
        assertEquals(3, cantidadHits(subsecuencia("cnti")));
    }

    @Test
    void subsecuenciaEncuentraConTokenCortoDeTresLetras() throws IOException {
        // El hueco que quedaba: 3 caracteres con una omision al medio.
        assertEquals(0, cantidadHits(subcadena("coa")));
        assertTrue(cantidadHits(subsecuencia("coa")) >= 1, "\"coa\" debe alcanzar a COCA COLA");
    }

    @Test
    void subsecuenciaEncuentraDosLetrasFaltantesEnPalabraCorta() throws IOException {
        // "piln" = PILSEN sin S ni E. Fuera del alcance de fuzzy en un token de 4.
        assertEquals(0, cantidadHits(subcadena("piln")));
        assertEquals(1, cantidadHits(subsecuencia("piln")));
    }

    @Test
    void subsecuenciaEstaAncladaEnLaPrimeraLetra() throws IOException {
        // No lleva `*` inicial a proposito: la primera letra ancla y recorta el ruido.
        // El caso "falta la primera letra" ya lo cubre la subcadena.
        assertEquals(0, cantidadHits(subsecuencia("nti")));
        assertEquals(3, cantidadHits(subcadena("nti")));
    }

    // ---------- pasada tolerante: fuzzy y token partido ----------

    @Test
    void fuzzyCubreLetraCambiadaQueLaSubsecuenciaNoAlcanza() throws IOException {
        // "ponti" no es subsecuencia de "conti" (letra cambiada, no omitida).
        assertEquals(0, cantidadHits(subsecuencia("ponti")));
        int maxEdits = ProductoTextoRelevanceScorer.distanciaFuzzyMaxima("ponti".length());
        assertTrue(maxEdits >= 1);
        assertEquals(3, cantidadHits(new FuzzyQuery(new Term(CAMPO, "ponti"), maxEdits)));
    }

    @Test
    void tokenPartidoEncuentraPalabrasPegadas() throws IOException {
        // "cocacola" no es ningun termino del indice: hay que partirlo en COCA + COLA.
        assertEquals(0, cantidadHits(prefijo("cocacola")));
        assertEquals(0, cantidadHits(subcadena("cocacola")));
        assertEquals(0, cantidadHits(subsecuencia("cocacola")));
        assertEquals(1, cantidadHits(partido("cocacola")));
    }

    @Test
    void umbralesHabilitanCadaMecanismoEnElLargoEsperado() {
        assertTrue("co".length() >= ProductoSearchService.MIN_LONGITUD_INFIJO);
        assertTrue("coa".length() >= ProductoSearchService.MIN_LONGITUD_SUBSECUENCIA);
        assertTrue("cocaco".length() >= ProductoSearchService.MIN_LONGITUD_TOKEN_PARTIDO);
        assertTrue("cocac".length() < ProductoSearchService.MIN_LONGITUD_TOKEN_PARTIDO);
    }

    @Test
    void metacaracteresTipeadosNoSeInterpretanComoComodin() throws IOException {
        // Un "*" tipeado por el usuario debe buscarse literal, no matchear todo.
        assertEquals(0, cantidadHits(prefijo("con*i")));
    }

    // ---------- helpers: replican los patrones que arma ProductoSearchService ----------

    private static void indexar(IndexWriter writer, String descripcion) throws IOException {
        Document doc = new Document();
        doc.add(new TextField(CAMPO, descripcion, Field.Store.YES));
        writer.addDocument(doc);
    }

    private Query prefijo(String token) {
        return wildcard(ProductoSearchService.escaparWildcard(normalizar(token)) + "*");
    }

    private Query subcadena(String token) {
        return wildcard("*" + ProductoSearchService.escaparWildcard(normalizar(token)) + "*");
    }

    private Query subsecuencia(String token) {
        return wildcard(ProductoSearchService.patronSubsecuencia(normalizar(token)));
    }

    /** Reproduce el OR de cortes que genera {@code agregarPredicadosTolerantes}. */
    private Query partido(String token) {
        String base = normalizar(token);
        BooleanQuery.Builder cortes = new BooleanQuery.Builder();
        int minParte = ProductoSearchService.MIN_LONGITUD_PARTE;
        for (int corte = minParte; corte <= base.length() - minParte; corte++) {
            BooleanQuery.Builder mitades = new BooleanQuery.Builder();
            mitades.add(wildcard(base.substring(0, corte) + "*"), BooleanClause.Occur.MUST);
            mitades.add(wildcard(base.substring(corte) + "*"), BooleanClause.Occur.MUST);
            cortes.add(mitades.build(), BooleanClause.Occur.SHOULD);
        }
        return cortes.build();
    }

    private static String normalizar(String token) {
        return ProductoTextoRelevanceScorer.normalizar(token);
    }

    private Query wildcard(String patron) {
        return new WildcardQuery(new Term(CAMPO, patron));
    }

    private int cantidadHits(Query query) throws IOException {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            return new IndexSearcher(reader).search(query, 100).scoreDocs.length;
        }
    }
}
