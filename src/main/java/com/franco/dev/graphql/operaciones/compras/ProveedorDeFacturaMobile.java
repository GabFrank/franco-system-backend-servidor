package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

import java.util.List;

/**
 * El emisor de una factura recibida, listo para darlo de alta como proveedor: los datos salen de la factura de
 * SIFEN (QR / CDC) o de lo que leyó la IA en la factura en papel.
 *
 * Si el RUC ya es de un proveedor, viene {@code proveedorId}; si ya existe una persona con ese documento (por
 * ejemplo, un cliente), viene {@code personaId} y el alta la reutiliza.
 */
@Data
public class ProveedorDeFacturaMobile {
    private Long documentoId;
    /** SIFEN o FOTO. */
    private String origen;
    /** RUC sin DV. */
    private String ruc;
    private String dv;
    /** El DV cierra con el RUC. En papel puede no cerrar (la IA leyó mal): entonces no se puede dar de alta. */
    private Boolean rucValido;
    private String nombre;
    private String nombreFantasia;
    private String direccion;
    private String ciudad;
    private String telefono;
    private String email;
    /** La factura es a crédito: sugerencia para el proveedor nuevo. */
    private Boolean credito;
    private Long proveedorId;
    private String proveedorNombre;
    private Long personaId;
    private String personaNombre;
    /**
     * Con persona existente: los campos que ya tiene cargados (nombreFantasia, direccion, telefono, email). Vienen
     * con el dato de la persona y no con el de la factura, porque el alta no los pisa.
     */
    private List<String> datosDePersona;
    /** Se puede registrar: hay RUC válido y todavía no es proveedor. */
    private Boolean puedeRegistrar;
    /** Por qué no se puede registrar, si no se puede. */
    private String motivo;
}
