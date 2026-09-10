package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TerminalPosRepository extends HelperRepository<TerminalPos, Long> {

    default Class<TerminalPos> getEntityClass() {
        return TerminalPos.class;
    }

    @Query("select t from TerminalPos t " +
            "where (UPPER(CAST(t.id as text)) like %?1% or UPPER(t.descripcion) like %?1% or UPPER(t.codigo) like %?1%)")
    public List<TerminalPos> findByAll(String texto);

    /**
     * Cuantas terminales apuntan a este formato.
     * <p>
     * Lo usa el ABM para no dejar desactivar un formato que este en uso: con la FK directa,
     * desactivarlo dejaria a esas terminales sin poder resolver el cupon, y el desktop bloquea la
     * venta con tarjeta cuando no hay formato. Un clic en una pantalla de administracion no puede
     * dejar sucursales enteras sin vender.
     */
    long countByFormatoTerminalPosId(Long formatoTerminalPosId);

    List<TerminalPos> findByFormatoTerminalPosIdOrderByIdAsc(Long formatoTerminalPosId);

    /**
     * Solo el id del formato asignado, sin traer la terminal entera.
     * <p>
     * Existe porque {@code saveTerminalPos} arma la entidad de cero con ModelMapper: lo que no
     * viene en el input nace en null y se persiste como null. Para no pisar el formato cuando el
     * cliente no lo manda hay que leer el valor actual — y traer la entidad completa la dejaria
     * asociada a la sesion, chocando con la que se esta por guardar con el mismo id.
     * <p>
     * Navegar {@code t.formatoTerminalPos.id} no genera JOIN: Hibernate lo traduce a la columna FK.
     */
    @Query("select t.formatoTerminalPos.id from TerminalPos t where t.id = :id")
    Long findFormatoTerminalPosIdDe(@Param("id") Long id);

    @Query(value = "select t from TerminalPos t " +
            "where (:descripcion is null or UPPER(t.descripcion) like %:descripcion%) " +
            "and (:codigo is null or UPPER(t.codigo) like %:codigo%) " +
            "and (:activo is null or t.activo = :activo) " +
            "order by t.id asc",
            countQuery = "select count(t) from TerminalPos t " +
                    "where (:descripcion is null or UPPER(t.descripcion) like %:descripcion%) " +
                    "and (:codigo is null or UPPER(t.codigo) like %:codigo%) " +
                    "and (:activo is null or t.activo = :activo)")
    public Page<TerminalPos> filterTerminalPos(@Param("descripcion") String descripcion,
                                               @Param("codigo") String codigo,
                                               @Param("activo") Boolean activo,
                                               Pageable pageable);

    TerminalPos findByCodigoIgnoreCase(String codigo);

    Long countByProveedorServicioId(Long proveedorServicioId);

    Page<TerminalPos> findAll(Pageable pageable);
}
