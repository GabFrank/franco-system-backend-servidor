package com.franco.dev.service.financiero;

import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Predicate;

/**
 * El número de comprobante de un documento de tesorería (issue #376): el que tipeó el usuario, si no
 * está repetido, o el siguiente de la serie.
 *
 * <p>Antes el central autonumeraba solo si el comprobante llegaba nulo, y el desktop manda la cadena
 * vacía: nunca autonumeraba. Y nada impedía dos documentos con el mismo número.</p>
 */
@Service
@RequiredArgsConstructor
public class ComprobanteNumeracionService {

    /** Largo de la columna {@code numero_comprobante}. */
    static final int LARGO_MAXIMO = 60;
    /** Números ya usados que la serie puede saltar en un alta antes de darse por vencida. */
    static final int MAXIMO_DE_SALTOS = 1000;

    private final ComprobanteSerieService comprobanteSerieService;
    private final BloqueoTransaccionalService bloqueo;

    /**
     * @param tipo     tipo de serie y de documento («ENTRADA_VARIA», «OPERACION_FINANCIERA»)
     * @param recibido lo que mandó el cliente; nulo, vacío o espacios = sin número
     * @param usado    ¿ya lo tiene otro documento no anulado de este tipo? Recibe el número normalizado
     * @param nombre   cómo nombrar el documento en el rechazo («una entrada varia»)
     * @return el número a guardar, o nulo si no se tipeó ninguno y no hay serie
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String resolver(String tipo, String recibido, Predicate<String> usado, String nombre) {
        String tipeado = normalizar(recibido);
        if (tipeado != null) {
            if (tipeado.length() > LARGO_MAXIMO) {
                throw new GraphQLException("El número de comprobante admite hasta " + LARGO_MAXIMO + " caracteres.");
            }
            // El lock antes de buscar: sin él, dos altas simultáneas con el mismo número pasan las dos.
            bloqueo.tomar(tipo + ":" + tipeado);
            if (usado.test(tipeado)) {
                throw new GraphQLException("Ya existe " + nombre + " con el comprobante " + tipeado + ".");
            }
            return tipeado;
        }
        // Autonumerado: si el número ya estaba usado —alguien lo tipeó antes— se pide el siguiente. No se
        // rechaza: el rollback desharía el avance del correlativo y cada alta volvería a generar el mismo.
        for (int salto = 0; salto <= MAXIMO_DE_SALTOS; salto++) {
            String generado = normalizar(comprobanteSerieService.siguienteNumero(tipo));
            if (generado == null) return null;   // sin serie: el documento queda sin número
            bloqueo.tomar(tipo + ":" + generado);
            if (!usado.test(generado)) return generado;
        }
        throw new GraphQLException("No se pudo asignar un número de comprobante: la serie " + tipo
                + " está generando números ya usados. Revisá su correlativo.");
    }

    /** Sin espacios alrededor y en mayúsculas; vacío = nulo. Es el valor que se guarda, se busca y se lockea. */
    static String normalizar(String numero) {
        if (numero == null) return null;
        String limpio = numero.trim().toUpperCase();
        return limpio.isEmpty() ? null : limpio;
    }
}
