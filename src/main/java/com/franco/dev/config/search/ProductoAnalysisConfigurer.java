package com.franco.dev.config.search;

import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurationContext;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;

/**
 * Analyzer simple: minúsculas + sin tildes + tokenización estándar.
 * Sin sinónimos ni reglas de negocio.
 */
public class ProductoAnalysisConfigurer implements LuceneAnalysisConfigurer {

    public static final String PRODUCTO_ANALYZER = "producto_analyzer";

    @Override
    public void configure(LuceneAnalysisConfigurationContext context) {
        context.analyzer(PRODUCTO_ANALYZER).custom()
                .tokenizer("standard")
                .tokenFilter("lowercase")
                .tokenFilter("asciifolding");

        context.normalizer("lowercase").custom()
                .tokenFilter("lowercase");
    }
}
