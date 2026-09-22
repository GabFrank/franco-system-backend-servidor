package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface FormatoTerminalPosRepository extends HelperRepository<FormatoTerminalPos, Long> {

    default Class<FormatoTerminalPos> getEntityClass() {
        return FormatoTerminalPos.class;
    }

    List<FormatoTerminalPos> findByActivoTrueOrderByIdAsc();

    List<FormatoTerminalPos> findAllByOrderByIdAsc();

    List<FormatoTerminalPos> findByProveedorServicioIdOrderByIdAsc(Long proveedorServicioId);

    /**
     * Un formato con ese nombre bajo ese proveedor. Reemplaza al viejo
     * {@code findByProveedorServicioId}: en este modelo un proveedor SI puede tener varios
     * formatos --uno por modelo de aparato-- y lo que no se puede repetir es el nombre dentro del
     * proveedor.
     * <p>
     * El comodin (proveedor NULL) va por {@link #findByProveedorServicioIsNullAndNombre}: en SQL
     * dos NULL no son iguales, asi que un {@code = NULL} nunca matchearia.
     */
    FormatoTerminalPos findByProveedorServicioIdAndNombre(Long proveedorServicioId, String nombre);

    FormatoTerminalPos findByProveedorServicioIsNullAndNombre(String nombre);
}
