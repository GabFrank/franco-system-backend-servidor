package com.franco.dev.service.sifen.util;

/**
 * Valores de {@code documento_electronico.tipo_documento}, que es un VARCHAR libre y hoy siempre
 * vale "FACTURA". Son constantes y no un enum a proposito: un enum Java nuevo obligaria a
 * declararlo tambien en el .graphqls (SchemaEnumsSincronizadosTest) y a un CHECK en la migracion,
 * sin ganar nada — el campo ya viaja como String hacia el desktop.
 */
public final class TipoDocumentoElectronico {

    public static final String FACTURA = "FACTURA";
    public static final String NOTA_CREDITO = "NOTA_CREDITO";
    public static final String NOTA_REMISION = "NOTA_REMISION";

    private TipoDocumentoElectronico() {
    }
}
