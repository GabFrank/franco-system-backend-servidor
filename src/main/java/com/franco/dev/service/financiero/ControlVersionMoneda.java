package com.franco.dev.service.financiero;

import com.franco.dev.graphql.configuracion.VersionClienteGraphQL;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Un escritorio anterior a la moneda por modulo trata todo importe como de la moneda de la empresa:
 * frente a un precio de 4,39 USD cobraria 4 Gs. Antes de guardar algo en otra moneda que la principal
 * (un precio, un PDV) se exige que la version minima del escritorio este configurada, y si
 * {@code moneda.desktop.version-minima-requerida} esta puesta, que no sea menor que esa.
 */
@Service
public class ControlVersionMoneda {

    @Autowired
    private VersionClienteGraphQL versionCliente;

    @Value("${moneda.desktop.version-minima-requerida:}")
    private String requerida;

    public void exigirParaOtraMoneda(String que) {
        String configurada = versionCliente.versionMinimaDesktop();
        if (configurada == null) {
            throw new GraphQLException("VERSION_MINIMA: para " + que + " primero hay que configurar la version"
                    + " minima del escritorio (Moneda por modulo). Un escritorio viejo cobraria el importe como"
                    + " si fuera de la moneda de la empresa.");
        }
        if (requerida != null && !requerida.trim().isEmpty() && menor(configurada, requerida.trim())) {
            throw new GraphQLException("VERSION_MINIMA: para " + que + " la version minima del escritorio tiene"
                    + " que ser " + requerida.trim() + " o mayor (hoy " + configurada + ").");
        }
    }

    /** Solo la base X.Y.Z: una prerelease (4.2.0-alpha.345) cuenta como su release, ya trae lo de esa version. */
    static boolean menor(String a, String b) {
        String[] x = a.split("-", 2)[0].split("\\."), y = b.split("-", 2)[0].split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? entero(x[i]) : 0, q = i < y.length ? entero(y[i]) : 0;
            if (p != q) return p < q;
        }
        return false;
    }

    private static int entero(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
