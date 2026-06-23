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
        assertTrue(ProductoTextoRelevanceScorer.distanciaFuzzyMaxima(4) == 0);
        assertTrue(ProductoTextoRelevanceScorer.distanciaFuzzyMaxima(5) == 1);
        assertTrue(ProductoTextoRelevanceScorer.distanciaFuzzyMaxima(8) == 2);
    }

    private static Producto producto(String descripcion) {
        Producto producto = new Producto();
        producto.setDescripcion(descripcion);
        return producto;
    }

}
