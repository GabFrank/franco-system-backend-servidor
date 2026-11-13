package com.franco.dev.domain.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Resumen del crédito por convenio de la persona en sesión, leído del central de la otra empresa. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConvenioRemoto {
    /** Nombre de la otra empresa, de la configuración (ej. «Bodega»). */
    private String empresa;
    /** Tope del cliente en la otra empresa ({@code cliente.credito}). */
    private Double limite;
    /** Suma de {@code valorTotal} de sus convenios abiertos allá. */
    private Double utilizado;
}
