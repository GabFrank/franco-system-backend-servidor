package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** La linea del cliente en el ticket de venta no rompe la impresion por el largo del nombre. */
class VentaGraphQLLineaClienteTest {

    private static Cliente clienteConNombre(String nombre) {
        Persona persona = new Persona();
        persona.setNombre(nombre);
        Cliente cliente = new Cliente();
        cliente.setPersona(persona);
        return cliente;
    }

    @Test
    void unNombreCortoSaleEntero() {
        assertEquals("Cliente: JUAN PEREZ GOMEZ", VentaGraphQL.lineaCliente(clienteConNombre("JUAN PEREZ GOMEZ")));
    }

    @Test
    void unNombreDe22SaleEntero() {
        String nombre = "ABCDEFGHIJKLMNOPQRSTUV";
        assertEquals("Cliente: " + nombre, VentaGraphQL.lineaCliente(clienteConNombre(nombre)));
    }

    @Test
    void unNombreLargoSeRecortaA22() {
        assertEquals("Cliente: ABCDEFGHIJKLMNOPQRSTUV",
                VentaGraphQL.lineaCliente(clienteConNombre("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456")));
    }

    @Test
    void sinNombreNoHayLinea() {
        assertNull(VentaGraphQL.lineaCliente(clienteConNombre(null)));
    }

    @Test
    void sinPersonaNoHayLinea() {
        assertNull(VentaGraphQL.lineaCliente(new Cliente()));
    }
}
