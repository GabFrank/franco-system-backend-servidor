package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.NotaRemisionItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaRemision;
import com.franco.dev.domain.financiero.enums.OrigenNotaRemision;
import com.franco.dev.domain.financiero.enums.ResponsableEmisionNr;
import com.franco.dev.repository.financiero.NotaRemisionItemRepository;
import com.franco.dev.repository.financiero.NotaRemisionRepository;
import com.franco.dev.repository.financiero.TimbradoDetalleRepository;
import com.franco.dev.service.sifen.util.SerieDeNumeracionValidator;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Alta y baja de notas de remisión. No habla con SIFEN: eso es {@code SifenService} y el envío lo
 * encadena el resolver (D8 del plan, tres transacciones separadas).
 */
@Slf4j
@Service
public class NotaRemisionService extends CrudService<NotaRemision, NotaRemisionRepository, EmbebedPrimaryKey> {

    /** Ventana de SIFEN para cancelar un DE que no es factura. */
    public static final int HORAS_PARA_ANULAR = 168;

    private final NotaRemisionRepository repository;
    private final NotaRemisionItemRepository itemRepository;
    private final TimbradoDetalleRepository timbradoDetalleRepository;
    private final FacturacionSecurityService seg;
    private final SerieDeNumeracionValidator serieValidator;

    public NotaRemisionService(NotaRemisionRepository repository,
                               NotaRemisionItemRepository itemRepository,
                               TimbradoDetalleRepository timbradoDetalleRepository,
                               FacturacionSecurityService seg,
                               SerieDeNumeracionValidator serieValidator) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.timbradoDetalleRepository = timbradoDetalleRepository;
        this.seg = seg;
        this.serieValidator = serieValidator;
    }

    @Override
    public NotaRemisionRepository getRepository() {
        return repository;
    }

    public Optional<NotaRemision> findByIdAndSucursalId(Long id, Long sucursalId) {
        return repository.findByIdAndSucursalId(id, sucursalId);
    }

    public List<NotaRemisionItem> findItems(Long notaRemisionId, Long sucursalId) {
        return itemRepository.findByNotaRemision(notaRemisionId, sucursalId);
    }

    public Page<NotaRemision> findByFilters(Long sucursalId, LocalDateTime desde, LocalDateTime hasta,
                                            Pageable pageable) {
        return repository.findByFilters(sucursalId, desde, hasta, pageable);
    }

    public List<NotaRemision> findActivasByTransferencia(Long transferenciaId, Long sucursalId) {
        return repository.findActivasByTransferenciaIdAndSucursalId(transferenciaId, sucursalId);
    }

    /** Tope de ids por consulta: una página de la lista son 25, y un IN sin límite no tiene razón. */
    static final int MAX_TRANSFERENCIAS_POR_CONSULTA = 200;

    /**
     * Las notas activas de varias transferencias, para que la lista sepa de entrada cuáles ya tienen
     * y el menú diga «Imprimir». Antes se enteraba recién al hacer clic.
     */
    public List<NotaRemision> findActivasByTransferencias(List<Long> transferenciaIds) {
        if (transferenciaIds == null || transferenciaIds.isEmpty()) return Collections.emptyList();
        if (transferenciaIds.size() > MAX_TRANSFERENCIAS_POR_CONSULTA) {
            throw new GraphQLException("Se pueden consultar hasta " + MAX_TRANSFERENCIAS_POR_CONSULTA
                    + " transferencias por vez");
        }
        return repository.findActivasByTransferenciaIdIn(transferenciaIds);
    }

    /**
     * Crea la nota con su número de serie. Es la T1 del plan (D8): al terminar, número y nota están
     * commiteados, pase lo que pase después con SIFEN.
     */
    @Transactional
    public NotaRemision crear(NotaRemision nota, List<NotaRemisionItem> items) {
        seg.requireEmitir();
        validar(nota, items);

        // Lock pesimista sobre el timbrado: serializa la asignación del número entre emisiones
        // simultáneas. La UNIQUE (timbrado_detalle_id, numero_nota_remision) es la red debajo.
        TimbradoDetalle timbrado = timbradoDetalleRepository.lockById(nota.getTimbradoDetalleId())
                .orElseThrow(() -> new GraphQLException("No existe el timbrado indicado"));
        if (Boolean.FALSE.equals(timbrado.getActivo())) {
            throw new GraphQLException("El timbrado no está activo");
        }

        // El número sale del id de la fila, pero la serie ante la SET es establecimiento+punto:
        // si otra fila activa declara la misma, los dos contadores emiten el mismo número.
        serieValidator.exigirSerieSinColision(timbrado);
        nota.setNumeroNotaRemision(repository.findMaxNumeroByTimbradoDetalleId(timbrado.getId()) + 1);
        if (nota.getFecha() == null) {
            nota.setFecha(LocalDateTime.now());
        }
        nota.setActivo(true);
        if (nota.getId() == null) {
            nota.setId(repository.siguienteId());
        }

        NotaRemision guardada = repository.save(nota);

        List<NotaRemisionItem> guardados = new ArrayList<>();
        for (NotaRemisionItem item : items) {
            item.setNotaRemisionId(guardada.getId());
            item.setSucursalId(guardada.getSucursalId());
            if (item.getId() == null) {
                item.setId(itemRepository.siguienteId());
            }
            guardados.add(itemRepository.save(item));
        }
        log.info("Nota de remisión {} creada con número {} y {} ítems",
                guardada.getId(), guardada.getNumeroNotaRemision(), guardados.size());
        return guardada;
    }

    /**
     * Baja lógica. El evento de cancelación ante SIFEN lo dispara el resolver; acá solo se valida la
     * ventana de 168 h y se apaga la nota.
     */
    @Transactional
    public NotaRemision anular(Long id, Long sucursalId) {
        seg.requireEmitir();
        NotaRemision nota = repository.findByIdAndSucursalId(id, sucursalId)
                .orElseThrow(() -> new GraphQLException("No existe la nota de remisión"));
        if (Boolean.FALSE.equals(nota.getActivo())) {
            throw new GraphQLException("La nota de remisión ya está anulada");
        }
        if (nota.getFecha() != null
                && nota.getFecha().isBefore(LocalDateTime.now().minusHours(HORAS_PARA_ANULAR))) {
            throw new GraphQLException("SIFEN solo acepta la cancelación dentro de las "
                    + HORAS_PARA_ANULAR + " horas de emitido el documento");
        }
        nota.setActivo(false);
        return repository.save(nota);
    }

    private void validar(NotaRemision nota, List<NotaRemisionItem> items) {
        if (items == null || items.isEmpty()) {
            throw new GraphQLException("La nota de remisión necesita al menos un ítem");
        }
        if (nota.getSucursalId() == null) {
            throw new GraphQLException("Falta la sucursal de origen del traslado");
        }
        if (nota.getTimbradoDetalleId() == null) {
            throw new GraphQLException("Falta el timbrado");
        }
        if (nota.getOrigen() == null) {
            throw new GraphQLException("Falta el origen de la nota de remisión");
        }
        if (nota.getMotivoEmision() == null) {
            throw new GraphQLException("Falta el motivo del traslado");
        }
        if (nota.getResponsableEmision() == null) {
            nota.setResponsableEmision(ResponsableEmisionNr.EMISOR_FACTURA);
        }
        // SIFEN no admite receptor innominado en una nota de remisión
        if (esVacio(nota.getReceptorNombre())) {
            throw new GraphQLException("La nota de remisión necesita el nombre del receptor");
        }
        if (esVacio(nota.getReceptorRuc())) {
            throw new GraphQLException("La nota de remisión necesita el documento o RUC del receptor");
        }
        if (nota.getOrigen() == OrigenNotaRemision.TRANSFERENCIA && nota.getTransferenciaId() == null) {
            throw new GraphQLException("Falta la transferencia de origen");
        }
        if (nota.getOrigen() == OrigenNotaRemision.FACTURA && nota.getFacturaLegalId() == null) {
            throw new GraphQLException("Falta la factura de origen");
        }
        if (nota.getOrigen() == OrigenNotaRemision.TRANSFERENCIA
                && !repository.findActivasByTransferenciaId(nota.getTransferenciaId()).isEmpty()) {
            throw new GraphQLException("La transferencia ya tiene una nota de remisión activa");
        }
        // Mismo guard para el origen FACTURA, que no lo tenía: sin él, dos clicks en «Guardar»
        // creaban dos notas activas y quemaban dos números de la serie. Es el equivalente del que
        // NotaCreditoService ya hace con findActivasByFactura.
        if (nota.getOrigen() == OrigenNotaRemision.FACTURA
                && !repository.findActivasByFacturaLegalId(nota.getFacturaLegalId(),
                        nota.getSucursalId()).isEmpty()) {
            throw new GraphQLException("La factura ya tiene una nota de remisión activa");
        }
        if (nota.getMotivoEmision() == MotivoEmisionNotaRemision.TRASLADO_ENTRE_LOCALES
                && esVacio(nota.getReceptorRuc())) {
            // Para traslado entre locales SIFEN exige que el receptor sea la propia empresa
            throw new GraphQLException("Un traslado entre locales se emite a nombre de la propia empresa");
        }
        for (NotaRemisionItem item : items) {
            if (esVacio(item.getDescripcion())) {
                throw new GraphQLException("Un ítem sin descripción no puede viajar en la nota");
            }
            if (item.getCantidad() == null || item.getCantidad().signum() <= 0) {
                throw new GraphQLException("La cantidad de cada ítem tiene que ser mayor a cero");
            }
        }
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
