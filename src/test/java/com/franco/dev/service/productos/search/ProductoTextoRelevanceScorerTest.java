package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Producto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductoTextoRelevanceScorerTest {

    @Test
    void burguesaDebePuntuarMayorQueCoincidenciasDébiles() {
        Producto burguesa = producto("BURGUESA LATA 269 ML");
        Producto berg = producto("SOM BERG LECHE 500 ML");
        Producto burst = producto("WAKA POD REFILL 6000 PUFFS 2% STRAW. BURST");

        int scoreBurguesa = ProductoTextoRelevanceScorer.puntuar(burguesa, "BURGU");
        int scoreBerg = ProductoTextoRelevanceScorer.puntuar(berg, "BURGU");
        int scoreBurst = ProductoTextoRelevanceScorer.puntuar(burst, "BURGU");

        assertTrue(scoreBurguesa > scoreBerg);
        assertTrue(scoreBurguesa > scoreBurst);
        assertTrue(scoreBerg < scoreBurguesa);
    }

    @Test
    void ordenLibreDePalabrasMantienePrioridadPorPrefijo() {
        Producto ronTresLeones = producto("RON TRES LEONES 750 ML");
        Producto leonesRon = producto("LEONES RON EXTRA");

        int scoreOrdenNatural = ProductoTextoRelevanceScorer.puntuar(ronTresLeones, "tres leones ron");
        int scoreOrdenInvertido = ProductoTextoRelevanceScorer.puntuar(leonesRon, "tres leones ron");

        assertTrue(scoreOrdenNatural > 0);
        assertTrue(scoreOrdenInvertido > 0);
    }

    @Test
    void distanciaFuzzyEsAdaptativaSegunLongitud() {
        assertTrue(ProductoTextoRelevanceScorer.distanciaFuzzyMaxima(2) == 0);
        assertTrue(ProductoTextoRelevanceScorer.distanciaFuzzyMaxima(3) == 1);
        assertTrue(ProductoTextoRelevanceScorer.distanciaFuzzyMaxima(5) == 1);
        assertTrue(ProductoTextoRelevanceScorer.distanciaFuzzyMaxima(6) == 2);
        assertTrue(ProductoTextoRelevanceScorer.distanciaFuzzyMaxima(8) == 2);
    }

    @Test
    void coincidenciaAproximadaSeOrdenaPorDistanciaDeEdicion() {
        // Lo que hace usable la pasada tolerante: entre varios candidatos flojos,
        // el mas parecido tiene que quedar arriba y no empatar con el resto.
        Producto coca = producto("COCA COLA 2LT");
        Producto chocolate = producto("CHOCOLATE BLANCO 90G");

        int scoreCoca = ProductoTextoRelevanceScorer.puntuar(coca, "COA");
        int scoreChocolate = ProductoTextoRelevanceScorer.puntuar(chocolate, "COA");

        assertTrue(scoreCoca > scoreChocolate,
                "COCA (1 letra omitida) debe rankear sobre CHOCOLATE (coincidencia casual)");
    }

    @Test
    void coincidenciaAproximadaQuedaDebajoDeSubcadenaYPrefijo() {
        Producto prefijo = producto("CONTI BIER LATA 269ML");
        Producto contiene = producto("BEBIDA GARCONTI 500ML");
        Producto aproximado = producto("CANTI ALGO 500ML");

        int scorePrefijo = ProductoTextoRelevanceScorer.puntuar(prefijo, "CONTI");
        int scoreContiene = ProductoTextoRelevanceScorer.puntuar(contiene, "CONTI");
        int scoreAproximado = ProductoTextoRelevanceScorer.puntuar(aproximado, "CONTI");

        assertTrue(scorePrefijo > scoreContiene);
        assertTrue(scoreContiene > scoreAproximado);
    }

    @Test
    void multiTokenParcialQuedaDebajoDeMultiTokenCompleto() {
        // Con minimumShouldMatch la pasada tolerante deja pasar resultados a los que
        // les falta una palabra: tienen que quedar siempre debajo de los completos.
        Producto completo = producto("CONTI GASEOSA UVA 2LT");
        Producto parcial = producto("CONTI BIER LATA 269ML");

        int scoreCompleto = ProductoTextoRelevanceScorer.puntuar(completo, "CONTI GASEOSA");
        int scoreParcial = ProductoTextoRelevanceScorer.puntuar(parcial, "CONTI GASEOSA");

        assertTrue(scoreCompleto > scoreParcial);
    }

    @Test
    void subcadenaSinLaPrimeraLetraPuntuaPorEncimaDeCoincidenciaDebil() {
        Producto conti = producto("CONTI BIER LATA 269ML");
        Producto ajeno = producto("PILSEN LATA 350ML");

        int scoreConti = ProductoTextoRelevanceScorer.puntuar(conti, "ONTI");
        int scoreAjeno = ProductoTextoRelevanceScorer.puntuar(ajeno, "ONTI");

        assertTrue(scoreConti > scoreAjeno);
    }

    @Test
    void prefijoExactoRankeaPorEncimaDeSubcadena() {
        Producto conti = producto("CONTI BIER LATA 269ML");
        Producto subcadena = producto("BEBIDA GARCONTI 500ML");

        int scorePrefijo = ProductoTextoRelevanceScorer.puntuar(conti, "CONTI");
        int scoreSubcadena = ProductoTextoRelevanceScorer.puntuar(subcadena, "CONTI");

        assertTrue(scorePrefijo > scoreSubcadena);
    }

    private static Producto producto(String descripcion) {
        Producto producto = new Producto();
        producto.setDescripcion(descripcion);
        return producto;
    }

}
