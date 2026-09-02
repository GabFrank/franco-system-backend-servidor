package com.franco.dev.domain.financiero.enums;

/**
 * Conclusión del que investiga un caso de retiro.
 *
 * <p>No confundir con {@link CategoriaDiferenciaRetiro}: esa es lo que <b>cree</b> el que recibe,
 * anotada por moneda en el momento de contar y sin haber averiguado nada. El veredicto es lo que
 * se <b>determinó</b> después, y es uno solo para todo el caso.
 *
 * <p>La diferencia entre lo declarado y lo contado no dice de qué lado está el error: el cajero
 * pudo contar mal o retener, y el de tesorería también. Cada veredicto nombra un lado.
 */
public enum VeredictoCasoRetiro {

    /**
     * Contó mal el que recibe. La acreditación en la caja mayor quedó equivocada, así que este
     * veredicto pide además anular la verificación y volver a contar: es el único que obliga a
     * tocar plata ya asentada.
     */
    ERROR_DE_CONTEO_TESORERIA,

    /**
     * Al sobre le faltó plata: vino menos de lo declarado.
     *
     * <p>Se mide <b>desde el sobre</b>, no desde la caja del cajero. El mismo hecho visto desde
     * la caja se ve al revés — si el cajero declaró 120 y mandó 110, al sobre le faltan 10 y a
     * su caja le sobran 10 — y confundir el punto de referencia invierte la clasificación.
     */
    FALTANTE_PDV,

    /** Al sobre le sobró plata: vino más de lo declarado. Mismo punto de referencia. */
    SOBRANTE_PDV,

    /** La diferencia se repuso después, normalmente en un retiro posterior. */
    REINTEGRADO,

    /** No se pudo determinar de qué lado estuvo; la empresa la asume. */
    ASUMIDO_SIN_RESPONSABLE
}
