package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.repository.financiero.TerminalPosRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class TerminalPosService extends CrudService<TerminalPos, TerminalPosRepository, Long> {

    private final TerminalPosRepository repository;

    @Override
    public TerminalPosRepository getRepository() {
        return repository;
    }

    public Long countByProveedorServicioId(Long proveedorServicioId) {
        return repository.countByProveedorServicioId(proveedorServicioId);
    }

    public TerminalPos findByCodigo(String codigo) {
        return repository.findByCodigoIgnoreCase(codigo);
    }

    public List<TerminalPos> searchByAll(String texto) {
        texto = texto != null ? texto.toUpperCase() : "";
        return repository.findByAll(texto);
    }

    public Page<TerminalPos> filter(String descripcion, String codigo, String serie, Long sucursalId,
                                    Boolean activo, int page, int size) {
        descripcion = normalizarBusqueda(descripcion);
        codigo = normalizarBusqueda(codigo);
        serie = normalizarBusqueda(serie);
        return repository.filterTerminalPos(descripcion, codigo, serie, sucursalId, activo,
                PageRequest.of(page, size));
    }

    private static String normalizarBusqueda(String v) {
        return (v != null && !v.trim().isEmpty()) ? v.trim().toUpperCase() : null;
    }

    @Override
    public TerminalPos save(TerminalPos entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        normalizarSerie(entity);
        validarSerieUnica(entity);
        validarCodigoUnico(entity);
        return super.save(entity);
    }

    /**
     * La serie se guarda en mayusculas y sin espacios al borde.
     * <p>
     * Los dos indices unicos de {@code V224.5} comparan la columna cruda: sin normalizar,
     * {@code jf798sjj} y {@code JF798SJJ} son dos maquinas distintas para Postgres y una sola para
     * cualquier persona. Y la serie se tipea mirando una etiqueta pegada al aparato, que es
     * exactamente donde aparecen esas diferencias.
     * <p>
     * Vacio se guarda como NULL, no como cadena vacia: los indices son parciales sobre
     * {@code serie IS NOT NULL}, asi que dos terminales con la serie en {@code ''} chocarian entre
     * si sin motivo.
     */
    private static void normalizarSerie(TerminalPos entity) {
        String s = entity.getSerie();
        if (s == null) return;
        s = s.trim().toUpperCase();
        entity.setSerie(s.isEmpty() ? null : s);
    }

    /**
     * Rechaza una serie repetida con una frase, en vez de con el error del indice unico.
     * <p>
     * Son dos consultas por el mismo motivo por el que son dos indices: en SQL dos NULL no son
     * iguales, asi que una comparacion por {@code proveedor_servicio_id} nunca encontraria los
     * comodines --que hoy son las dos terminales que existen--.
     */
    private void validarSerieUnica(TerminalPos entity) {
        if (entity.getSerie() == null) return;
        Long proveedorId = entity.getProveedorServicio() != null
                ? entity.getProveedorServicio().getId() : null;

        TerminalPos existente = proveedorId != null
                ? repository.findByProveedorServicioIdAndSerie(proveedorId, entity.getSerie())
                : repository.findByProveedorServicioIsNullAndSerie(entity.getSerie());

        if (existente != null && !existente.getId().equals(entity.getId())) {
            throw new GraphQLException("La serie \"" + entity.getSerie() + "\" ya esta registrada en"
                    + " la terminal \"" + descripcionDe(existente) + "\". Dos aparatos no pueden"
                    + " compartir identificador: el cupon no diria de cual salio.");
        }
    }

    /**
     * El codigo es lo que el cajero escanea para elegir la terminal, y el dialogo de escaneo se
     * queda con el primer resultado. Dos terminales con el mismo codigo y el cobro va contra la
     * maquina equivocada, sin ningun aviso.
     * <p>
     * {@code V224.5} lo cierra con un indice unico parcial; esto es para que el operador vea una
     * frase y no el error de Postgres.
     */
    private void validarCodigoUnico(TerminalPos entity) {
        String codigo = entity.getCodigo();
        if (codigo == null || codigo.trim().isEmpty()) return;

        TerminalPos existente = repository.findByCodigoIgnoreCase(codigo.trim());
        if (existente != null && !existente.getId().equals(entity.getId())) {
            throw new GraphQLException("El codigo \"" + codigo.trim() + "\" ya lo usa la terminal \""
                    + descripcionDe(existente) + "\". Es lo que el cajero escanea para elegir la"
                    + " maquina: repetido, cobraria contra la equivocada.");
        }
    }

    private static String descripcionDe(TerminalPos t) {
        return t.getDescripcion() != null && !t.getDescripcion().trim().isEmpty()
                ? t.getDescripcion() : ("#" + t.getId());
    }
}
