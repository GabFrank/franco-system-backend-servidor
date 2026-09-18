package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaCredito;
import com.franco.dev.repository.financiero.NotaCreditoItemRepository;
import com.franco.dev.repository.financiero.NotaCreditoRepository;
import com.franco.dev.repository.financiero.TimbradoDetalleRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Alta y baja de notas de crédito. En esta entrega **solo NC total**: la nota copia 1:1 los ítems y
 * los totales de la factura.
 *
 * Por qué se copia y no se recalcula: recalcular sobre los mismos datos introduce diferencias de
 * redondeo contra la factura que se está acreditando, y eso es una inconsistencia fiscal que SIFEN
 * ve en los totales del XML.
 */
@Slf4j
@Service
public class NotaCreditoService extends CrudService<NotaCredito, NotaCreditoRepository, EmbebedPrimaryKey> {

    /** Ventana de SIFEN para cancelar un DE que no es factura. */
    public static final int HORAS_PARA_ANULAR = 168;

    private final NotaCreditoRepository repository;
    private final NotaCreditoItemRepository itemRepository;
    private final TimbradoDetalleRepository timbradoDetalleRepository;
    private final FacturaLegalService facturaLegalService;
    private final FacturaLegalItemService facturaLegalItemService;
    private final DocumentoElectronicoService documentoElectronicoService;
    private final FacturacionSecurityService seg;

    public NotaCreditoService(NotaCreditoRepository repository,
                              NotaCreditoItemRepository itemRepository,
                              TimbradoDetalleRepository timbradoDetalleRepository,
                              FacturaLegalService facturaLegalService,
                              FacturaLegalItemService facturaLegalItemService,
                              DocumentoElectronicoService documentoElectronicoService,
                              FacturacionSecurityService seg) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.timbradoDetalleRepository = timbradoDetalleRepository;
        this.facturaLegalService = facturaLegalService;
        this.facturaLegalItemService = facturaLegalItemService;
        this.documentoElectronicoService = documentoElectronicoService;
        this.seg = seg;
    }

    @Override
    public NotaCreditoRepository getRepository() {
        return repository;
    }

    public Optional<NotaCredito> findByIdAndSucursalId(Long id, Long sucursalId) {
        return repository.findByIdAndSucursalId(id, sucursalId);
    }

    public List<NotaCreditoItem> findItems(Long notaCreditoId, Long sucursalId) {
        return itemRepository.findByNotaCredito(notaCreditoId, sucursalId);
    }

    public List<NotaCredito> findActivasByFactura(Long facturaLegalId, Long sucursalId) {
        return repository.findActivasByFactura(facturaLegalId, sucursalId);
    }

    public Page<NotaCredito> findByFilters(Long sucursalId, LocalDateTime desde, LocalDateTime hasta,
                                           Pageable pageable) {
        return repository.findByFilters(sucursalId, desde, hasta, pageable);
    }

    /**
     * Crea la NC total de una factura. Es la T1 del envío (D8): al terminar, el número y la nota
     * están commiteados pase lo que pase después con SIFEN.
     */
    @Transactional
    public NotaCredito crearDesdeFactura(Long facturaLegalId, Long sucursalId,
                                         MotivoEmisionNotaCredito motivo, String descripcionMotivo,
                                         Long usuarioId) {
        seg.requireEmitir();

        if (motivo == null) {
            throw new GraphQLException("Falta el motivo de la nota de crédito");
        }
        FacturaLegal factura = facturaLegalService.findByIdAndSucursalId(facturaLegalId, sucursalId);
        if (factura == null) {
            throw new GraphQLException("No existe la factura " + facturaLegalId
                    + " en la sucursal " + sucursalId);
        }
        if (Boolean.FALSE.equals(factura.getActivo())) {
            throw new GraphQLException("La factura está anulada: no se le puede emitir una nota de crédito");
        }

        // La NC se emite contra una factura ELECTRONICA aprobada: su CDC es el documento asociado
        // que va en el XML, y sin aprobación SIFEN rechaza la referencia.
        DocumentoElectronico deFactura = documentoElectronicoService
                .findByFacturaLegalId(facturaLegalId, sucursalId)
                .orElseThrow(() -> new GraphQLException(
                        "La factura no es electrónica: no tiene documento electrónico asociado"));
        if (deFactura.getEstado() != EstadoDE.APROBADO) {
            throw new GraphQLException("La factura todavía no está aprobada por SIFEN (estado "
                    + deFactura.getEstado() + "): no se le puede emitir una nota de crédito");
        }
        if (deFactura.getCdc() == null || deFactura.getCdc().trim().isEmpty()) {
            throw new GraphQLException("La factura no tiene CDC");
        }

        // MVP: NC total. Una segunda NC total duplicaría el crédito.
        if (!repository.findActivasByFactura(facturaLegalId, sucursalId).isEmpty()) {
            throw new GraphQLException("La factura ya tiene una nota de crédito activa");
        }

        List<FacturaLegalItem> itemsFactura = facturaLegalItemService
                .findByFacturaLegalId(facturaLegalId, sucursalId);
        if (itemsFactura == null || itemsFactura.isEmpty()) {
            throw new GraphQLException("La factura no tiene ítems");
        }

        TimbradoDetalle timbrado = timbradoDetalleRepository.lockById(factura.getTimbradoDetalle() != null
                        ? factura.getTimbradoDetalle().getId() : null)
                .orElseThrow(() -> new GraphQLException("La factura no tiene timbrado"));

        NotaCredito nota = new NotaCredito();
        nota.setId(repository.siguienteId());
        nota.setSucursalId(sucursalId);
        nota.setTimbradoDetalleId(timbrado.getId());
        nota.setNumeroNotaCredito(repository.findMaxNumeroByTimbradoDetalleId(timbrado.getId()) + 1);
        nota.setFecha(LocalDateTime.now());
        nota.setFacturaLegalId(facturaLegalId);
        nota.setMotivoEmision(motivo);
        nota.setDescripcionMotivo(descripcionMotivo);
        nota.setActivo(true);
        nota.setUsuarioId(usuarioId);

        // Receptor: snapshot de la factura, no del cliente actual (pudo cambiar desde entonces).
        nota.setClienteId(factura.getCliente() != null ? factura.getCliente().getId() : null);
        nota.setNombre(factura.getNombre());
        nota.setRuc(factura.getRuc());
        nota.setDireccion(factura.getDireccion());

        // Moneda heredada: una NC en otra moneda que su factura es un rechazo de SIFEN.
        nota.setMonedaExtranjera(factura.getMonedaExtranjera());
        nota.setTipoCambio(decimal(factura.getTipoCambio()));

        // Totales copiados 1:1 (NC total).
        nota.setIvaParcial0(decimal(factura.getIvaParcial0()));
        nota.setIvaParcial5(decimal(factura.getIvaParcial5()));
        nota.setIvaParcial10(decimal(factura.getIvaParcial10()));
        nota.setTotalParcial0(decimal(factura.getTotalParcial0()));
        nota.setTotalParcial5(decimal(factura.getTotalParcial5()));
        nota.setTotalParcial10(decimal(factura.getTotalParcial10()));
        nota.setDescuento(decimal(factura.getDescuento()));
        nota.setTotalFinal(decimal(factura.getTotalFinal()));

        NotaCredito guardada = repository.save(nota);

        List<NotaCreditoItem> items = new ArrayList<>();
        for (FacturaLegalItem origen : itemsFactura) {
            NotaCreditoItem item = new NotaCreditoItem();
            item.setId(itemRepository.siguienteId());
            item.setSucursalId(sucursalId);
            item.setNotaCreditoId(guardada.getId());
            item.setFacturaLegalItemId(origen.getId());
            item.setProductoId(origen.getProducto() != null ? origen.getProducto().getId() : null);
            item.setPresentacionId(origen.getPresentacion() != null ? origen.getPresentacion().getId() : null);
            item.setDescripcion(origen.getDescripcion());
            item.setCantidad(origen.getCantidad() != null
                    ? BigDecimal.valueOf(origen.getCantidad()) : BigDecimal.ONE);
            item.setUnidadMedida(origen.getUnidadMedida());
            item.setPrecioUnitario(decimal(origen.getPrecioUnitario()));
            item.setTotal(decimal(origen.getTotal()));
            item.setIva(origen.getIva());
            items.add(itemRepository.save(item));
        }

        log.info("Nota de crédito {} creada con número {} sobre la factura {} ({} ítems)",
                guardada.getId(), guardada.getNumeroNotaCredito(), facturaLegalId, items.size());
        return guardada;
    }

    /** Baja lógica; el evento de cancelación ante SIFEN lo dispara el resolver. */
    @Transactional
    public NotaCredito anular(Long id, Long sucursalId) {
        seg.requireEmitir();
        NotaCredito nota = repository.findByIdAndSucursalId(id, sucursalId)
                .orElseThrow(() -> new GraphQLException("No existe la nota de crédito"));
        if (Boolean.FALSE.equals(nota.getActivo())) {
            throw new GraphQLException("La nota de crédito ya está anulada");
        }
        if (nota.getFecha() != null
                && nota.getFecha().isBefore(LocalDateTime.now().minusHours(HORAS_PARA_ANULAR))) {
            throw new GraphQLException("SIFEN solo acepta la cancelación dentro de las "
                    + HORAS_PARA_ANULAR + " horas de emitido el documento");
        }
        nota.setActivo(false);
        return repository.save(nota);
    }

    private static BigDecimal decimal(Double valor) {
        return valor != null ? BigDecimal.valueOf(valor) : null;
    }

    /**
     * Facturas que HOY admiten nota de crédito, para el buscador del botón «Adicionar».
     *
     * Aplica los mismos CINCO requisitos que {@link #crearDesdeFactura}: electrónica con DE
     * aprobado y CDC, activa, sin nota de crédito activa y con ítems. Filtrar acá y no al emitir
     * es la diferencia entre no ver una factura y verla, elegirla y recibir un error.
     */
    public List<FacturaParaNotaCredito> facturasParaNotaCredito(Long sucursalId, String numero,
                                                                int page, int size) {
        seg.requireEmitir();
        if (sucursalId == null) {
            throw new GraphQLException("Falta la sucursal");
        }
        List<FacturaParaNotaCredito> candidatas = new ArrayList<>();
        for (FacturaLegal factura : facturaLegalService.buscarCandidatasANotaCredito(
                sucursalId, numero, page, size)) {
            if (Boolean.FALSE.equals(factura.getActivo())) continue;

            DocumentoElectronico de = documentoElectronicoService
                    .findByFacturaLegalId(factura.getId(), sucursalId).orElse(null);
            if (de == null || de.getEstado() != EstadoDE.APROBADO) continue;
            if (de.getCdc() == null || de.getCdc().trim().isEmpty()) continue;

            if (!repository.findActivasByFactura(factura.getId(), sucursalId).isEmpty()) continue;

            List<FacturaLegalItem> items = facturaLegalItemService
                    .findByFacturaLegalId(factura.getId(), sucursalId);
            if (items == null || items.isEmpty()) continue;

            candidatas.add(new FacturaParaNotaCredito(
                    factura.getId(), sucursalId,
                    factura.getNumeroFactura() != null ? factura.getNumeroFactura().intValue() : null,
                    factura.getFecha() != null ? factura.getFecha().toString() : null,
                    factura.getNombre(), factura.getRuc(),
                    factura.getTotalFinal() != null ? factura.getTotalFinal().doubleValue() : null,
                    factura.getMonedaExtranjera() != null ? factura.getMonedaExtranjera() : "GS"));
        }
        return candidatas;
    }

    /** Lo que el buscador muestra para que el operador confirme que eligió bien. */
    public static class FacturaParaNotaCredito {
        private final Long facturaLegalId;
        private final Long sucursalId;
        private final Integer numeroFactura;
        private final String fecha;
        private final String cliente;
        private final String ruc;
        private final Double total;
        private final String moneda;

        public FacturaParaNotaCredito(Long facturaLegalId, Long sucursalId, Integer numeroFactura,
                                      String fecha, String cliente, String ruc, Double total,
                                      String moneda) {
            this.facturaLegalId = facturaLegalId;
            this.sucursalId = sucursalId;
            this.numeroFactura = numeroFactura;
            this.fecha = fecha;
            this.cliente = cliente;
            this.ruc = ruc;
            this.total = total;
            this.moneda = moneda;
        }

        public Long getFacturaLegalId() { return facturaLegalId; }
        public Long getSucursalId() { return sucursalId; }
        public Integer getNumeroFactura() { return numeroFactura; }
        public String getFecha() { return fecha; }
        public String getCliente() { return cliente; }
        public String getRuc() { return ruc; }
        public Double getTotal() { return total; }
        public String getMoneda() { return moneda; }
    }

}
