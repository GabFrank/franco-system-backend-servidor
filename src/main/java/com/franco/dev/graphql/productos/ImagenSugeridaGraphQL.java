package com.franco.dev.graphql.productos;

import com.franco.dev.service.productos.imagenes.DescargaImagenService;
import com.franco.dev.service.productos.imagenes.ParecidasImagen;
import com.franco.dev.service.productos.imagenes.SugerenciaImagenService;
import com.franco.dev.service.productos.imagenes.SugerenciasImagen;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class ImagenSugeridaGraphQL implements GraphQLQueryResolver {

    @Autowired
    private SugerenciaImagenService sugerenciaImagenService;

    @Autowired
    private DescargaImagenService descargaImagenService;

    public SugerenciasImagen sugerenciasImagenPresentacion(Long presentacionId, String codigo, String texto) {
        return sugerenciaImagenService.sugerir(presentacionId, codigo, texto);
    }

    public String descargarImagenSugerida(String url) {
        return descargaImagenService.descargar(url);
    }

    public ParecidasImagen imagenesParecidasDe(String url, String texto, String similares) {
        return sugerenciaImagenService.parecidasDe(url, texto, similares);
    }
}
