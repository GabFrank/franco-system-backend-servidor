package com.franco.dev.controller.financiero;

import com.franco.dev.service.financiero.CapturaMuestraService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La foto guardada de una muestra.
 *
 * <p><b>Controller aparte, y no un metodo mas en {@code CapturaMuestraController}</b>, porque el
 * espacio de rutas es distinto y la diferencia importa. Aquel cuelga de {@code /public}: lo abre un
 * telefono sin sesion, autorizado por un token que vence en minutos y que solo habilita SUBIR una
 * imagen. Esto DEVUELVE cupones reales --importe, numero de boleta, serie de la terminal-- y las
 * filas viven meses. Eso pide sesion, y {@code /api/**} ya es el espacio autenticado de
 * {@code SecurityConfig}.
 *
 * <p>Se sirve como bytes y no como URL de archivo: el directorio de imagenes es configurable y no
 * tiene por que estar publicado por el servidor web.
 *
 * <p><b>Y exige el mismo rol que el ABM de muestras</b> ({@code requireVer}), no solo sesion. Sin
 * eso, cualquier usuario logueado --un cajero con VENTA TOUCH-- podia enumerar {@code id} (es un
 * correlativo) y bajar la foto de cualquier muestra: importe, boleta y serie de terminales de
 * otras sucursales. Se encontro en la auditoria del 2026-09-21: este endpoint nacio junto con el
 * fix del filtro JWT que hizo que {@code /api/**} recien autentique, asi que paso de inalcanzable
 * a alcanzable sin que nadie lo hubiera gateado. {@code CapturaMuestraGraphQL} ya pedia el rol en
 * cada metodo; este era el unico hueco.
 */
@RestController
@RequestMapping("/api/captura-muestra")
public class CapturaMuestraImagenController {

    private final CapturaMuestraService service;
    private final TesoreriaSecurityService seg;

    public CapturaMuestraImagenController(CapturaMuestraService service, TesoreriaSecurityService seg) {
        this.service = service;
        this.seg = seg;
    }

    @GetMapping(value = "/imagen/{id}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> imagen(@PathVariable Long id) {
        seg.requireVer();
        return service.imagenDe(id)
                .map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(bytes))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).<byte[]>build());
    }
}
