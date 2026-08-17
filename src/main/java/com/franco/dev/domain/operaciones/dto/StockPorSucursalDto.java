package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Existencia de un producto en una sucursal.
 *
 * Existe para poder devolver la existencia de TODAS las sucursales en una
 * sola consulta. Sin esto, el cliente tiene que llamar
 * {@code productoPorSucursalStock} una vez por sucursal: hoy son 18 llamadas
 * para responder una sola pregunta, y el navegador solo abre 6 conexiones por
 * origen, asi que salen en tandas.
 *
 * Trae unicamente {@code sucursalId} y {@code cantidad} a proposito. El campo
 * {@code Producto.sucursales} de {@link com.franco.dev.graphql.productos.resolver.ProductoResolver}
 * ya devuelve algo parecido, pero ademas resuelve costo medio, ultima compra,
 * pedido y cantidades minima/maxima, y por dentro itera sucursal por sucursal:
 * mas trabajo de base para datos que nadie pidio. (Ese campo, ademas, no esta
 * declarado en el schema, asi que hoy no se puede consultar.)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockPorSucursalDto {
    private Long sucursalId;
    private Double cantidad;
}
