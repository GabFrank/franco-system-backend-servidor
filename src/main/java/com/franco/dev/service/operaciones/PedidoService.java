package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoResumen;
import com.franco.dev.domain.operaciones.ProcesoEtapa;

import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.repository.operaciones.PedidoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.personas.VendedorProveedorService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.dateToString;
import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import com.franco.dev.service.operaciones.NotaRecepcionService;

@Service
@AllArgsConstructor
public class PedidoService extends CrudService<Pedido, PedidoRepository, Long> {
    private final PedidoRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private VendedorProveedorService vendedorProveedorService;
    
    @Autowired
    private ProcesoEtapaService procesoEtapaService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    @Override
    public PedidoRepository getRepository() {
        return repository;
    }

    public Page<Pedido> filterPedidos(Long idPedido,
                                      Integer numeroNotaRecepcion, Long sucursalId, String inicio, String fin, Long proveedorId, Long vendedorId, Long formaPago, Long productoId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        if(idPedido!=null) return repository.findById(idPedido, pageable);
        if(numeroNotaRecepcion != null) return repository.filterPedidosByNumeroNota(numeroNotaRecepcion, pageable);
        return repository.filterPedidos(idPedido,
                numeroNotaRecepcion, sucursalId, stringToDate(inicio), stringToDate(fin), proveedorId, vendedorId, formaPago, productoId, pageable);
    }

//    public List<Pedido> filterPedidos(@Param("estado") String estado,@Param("sucursal_id") Long sucursalId,@Param("inicio") String inicio,@Param("fin") String fin,@Param("proveedor_id") Long proveedorId,@Param("vendedor_d") Long vendedorId,@Param("forma_pago") String formaPago,@Param("producto_id") Long productoId){
//        List<Pedido> pedidos = new ArrayList<>();
//        StringBuilder strQuery = new StringBuilder();
//               strQuery.append("select  " +
//                "distinct p.id,  " +
//                "p.moneda_id, " +
//                "p.proveedor_id,  " +
//                "p.vendedor_id,  " +
//                "p.forma_pago,  " +
//                "p.dias_cheque,  " +
//                "p.fecha_de_entrega,  " +
//                "p.usuario_id, " +
//                "p.cantidad_notas, " +
//                "p.descuento , " +
//                "p.necesidad_id  " +
//                "from operaciones.pedido p " +
//                "join operaciones.pedido_item pi2 on pi2.pedido_id = p.id " +
//                "join operaciones.pedido_item_sucursal pis on pis.pedido_item_id = pi2.id where 1=1 ");
//        if(estado!=null){
//            strQuery.append("and cast(p.estado as text) = cast(:estado as text) ");
//        }
//        if(sucursalId!=null){
//            strQuery.append("and pis.sucursal_id = :sucursal_id ");
//        }
//        if(inicio!=null){
//            strQuery.append("and cast(p.creado_en as Date) between cast(:inicio as Date) and cast(:fin as Date) ");
//        }
//        if(proveedorId!=null){
//            strQuery.append("and p.proveedor_id = :proveedor_id ");
//        }
//        if(vendedorId!=null){
//            strQuery.append("and p.vendedor_id = :vendedor_i ");
//        }
//        if(formaPago!=null){
//            strQuery.append("and p.forma_pago = :forma_pago ");
//        }
//        if(productoId!=null){
//            strQuery.append("and pi2.producto_id = :producto_id ");
//        }
//        Query q = entityManager.createNamedQuery(strQuery.toString(), Pedido.class);
//        return q.getResultList();
//    }

    @Override
    public Pedido save(Pedido entity) {
        // Establecer creadoEn si es null (para nuevos pedidos o pedidos existentes sin fecha)
        if (entity.getCreadoEn() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        Pedido e = super.save(entity);
        if (entity.getVendedor() != null && entity.getProveedor() != null) {
            vendedorProveedorService.save(entity.getVendedor(), entity.getProveedor());
        }
        return e;
    }

    /**
     * Obtiene el resumen del pedido con información actualizada
     * @param pedidoId ID del pedido
     * @return PedidoResumen con etapa actual, cantidad de ítems, valor total y estadísticas de distribución
     */
    public PedidoResumen getPedidoResumen(Long pedidoId) {
        if (pedidoId == null) {
            throw new IllegalArgumentException("ID del pedido es requerido");
        }

        // Obtener etapa actual
        ProcesoEtapa etapaActual = procesoEtapaService.getEtapaActual(pedidoId);
        
        // Obtener datos básicos desde el repositorio
        Long cantidadItems = repository.getCantidadItemsByPedidoId(pedidoId);
        Double valorTotal = repository.getValorTotalNotasRecepcionByPedidoId(pedidoId); // Valor de notas de recepción (facturas recibidas)
        Double valorTotalPedido = repository.getValorTotalByPedidoId(pedidoId); // Valor total del pedido (suma de items × precios)
        
        // Obtener estadísticas de distribución
        Long cantidadItemsConDistribucionCompleta = repository.getCantidadItemsConDistribucionCompleta(pedidoId);
        Long cantidadItemsPendientesDistribucion = cantidadItems - cantidadItemsConDistribucionCompleta;
        
        // Crear y retornar el resumen
        PedidoResumen resumen = new PedidoResumen();
        resumen.setPedidoId(pedidoId);
        resumen.setEtapaActual(etapaActual);
        resumen.setCantidadItems(cantidadItems != null ? cantidadItems : 0L);
        resumen.setValorTotal(valorTotal != null ? valorTotal : 0.0);
        resumen.setValorTotalPedido(valorTotalPedido != null ? valorTotalPedido : 0.0);
        resumen.setCantidadItemsConDistribucionCompleta(cantidadItemsConDistribucionCompleta != null ? cantidadItemsConDistribucionCompleta : 0L);
        resumen.setCantidadItemsPendientesDistribucion(cantidadItemsPendientesDistribucion != null ? cantidadItemsPendientesDistribucion : 0L);
        
        return resumen;
    }

    /**
     * Finaliza la creación de un pedido - NUEVA FUNCIONALIDAD
     * Cambia el estado del pedido y gestiona las etapas del proceso
     * AHORA USA PROCESOETAPA EN LUGAR DE PEDIDOESTADO
     */
    @Transactional
    public Pedido finalizarCreacion(Long pedidoId) {
        Pedido pedido = findById(pedidoId).orElseThrow(
            () -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId)
        );
        
        // Verificar que la etapa de creación esté en proceso usando ProcesoEtapa
        ProcesoEtapa etapaCreacion = procesoEtapaService.getEtapaByPedidoAndTipo(pedidoId, ProcesoEtapaTipo.CREACION)
            .orElseThrow(() -> new IllegalStateException("No se encontró la etapa de creación para el pedido: " + pedidoId));
        
        if (etapaCreacion.getEstadoEtapa() != ProcesoEtapaEstado.EN_PROCESO) {
            throw new IllegalStateException("Solo se pueden finalizar pedidos cuya etapa de creación esté en proceso");
        }
        
        // El estado del pedido ahora se maneja completamente a través de ProcesoEtapa
        Pedido pedidoFinalizado = save(pedido);
        
        // Finalizar etapa de creación usando ProcesoEtapa
        procesoEtapaService.finalizarEtapa(pedidoId, ProcesoEtapaTipo.CREACION);
        
        // Crear la siguiente etapa (Recepción de Nota) como pendiente
        procesoEtapaService.crearEtapaSiguiente(pedido, ProcesoEtapaTipo.CREACION);
        
        return pedidoFinalizado;
    }

    /**
     * Finaliza la etapa de recepción de notas y avanza a la siguiente etapa
     * @param pedidoId - ID del pedido
     * @return Pedido actualizado
     */
    @Transactional
    public Pedido finalizarRecepcionNotas(Long pedidoId) {
        Pedido pedido = findById(pedidoId).orElseThrow(
            () -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId)
        );
        
        // Verificar que la etapa de recepción de notas esté en proceso
        ProcesoEtapa etapaRecepcionNota = procesoEtapaService.getEtapaByPedidoAndTipo(pedidoId, ProcesoEtapaTipo.RECEPCION_NOTA)
            .orElseThrow(() -> new IllegalStateException("No se encontró la etapa de recepción de notas para el pedido: " + pedidoId));
        
        if (etapaRecepcionNota.getEstadoEtapa() != ProcesoEtapaEstado.EN_PROCESO) {
            throw new IllegalStateException("Solo se pueden finalizar pedidos cuya etapa de recepción de notas esté en proceso");
        }
        
        // Validar que existan notas registradas
        List<NotaRecepcion> notas = notaRecepcionService.findByPedidoId(pedidoId);
        if (notas.isEmpty()) {
            throw new IllegalStateException("Debe registrar al menos una nota de recepción para finalizar la conciliación");
        }
        
        // CAMBIAR ESTADO DE LAS NOTAS DE PENDIENTE_CONCILIACION A CONCILIADA
        for (NotaRecepcion nota : notas) {
            if (nota.getEstado() == NotaRecepcionEstado.PENDIENTE_CONCILIACION) {
                nota.setEstado(NotaRecepcionEstado.CONCILIADA);
                notaRecepcionService.save(nota);
            }
        }
        
        // Finalizar etapa de recepción de notas
        procesoEtapaService.finalizarEtapa(pedidoId, ProcesoEtapaTipo.RECEPCION_NOTA);
        
        // Crear la siguiente etapa (Recepción de Mercadería) como pendiente
        procesoEtapaService.crearEtapaSiguiente(pedido, ProcesoEtapaTipo.RECEPCION_NOTA);
        
        return pedido;
    }

    /**
     * Busca pedidos con filtros usando Criteria Builder
     */
    public Page<Pedido> findPedidosWithFilters(Long sucursalId, Long productoId, Long proveedorId, 
                                                String estado, LocalDateTime creadoDesde, LocalDateTime creadoHasta,
                                                Pageable pageable) {
        
        // Construir query usando CriteriaBuilder
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Pedido> query = cb.createQuery(Pedido.class);
        Root<Pedido> root = query.from(Pedido.class);
        
        // Lista de predicados para los filtros
        List<Predicate> predicates = new ArrayList<>();
        
        // Filtro por proveedor
        if (proveedorId != null) {
            predicates.add(cb.equal(root.get("proveedor").get("id"), proveedorId));
        }
        
        // Filtro por fecha inicio
        if (creadoDesde != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), creadoDesde));
        }
        
        // Filtro por fecha fin
        if (creadoHasta != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("creadoEn"), creadoHasta));
        }
        
        // Filtro por producto - usar subquery desde PedidoItem
        if (productoId != null) {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<com.franco.dev.domain.operaciones.PedidoItem> pedidoItemRoot = subquery.from(com.franco.dev.domain.operaciones.PedidoItem.class);
            subquery.select(pedidoItemRoot.get("pedido").get("id"));
            subquery.where(cb.equal(pedidoItemRoot.get("producto").get("id"), productoId));
            predicates.add(cb.in(root.get("id")).value(subquery));
            query.distinct(true);
        }
        
        // Filtro por sucursal - buscar en PedidoSucursalEntrega o PedidoSucursalInfluencia
        if (sucursalId != null) {
            // Subquery para PedidoSucursalEntrega
            Subquery<Long> subqueryEntrega = query.subquery(Long.class);
            Root<com.franco.dev.domain.operaciones.PedidoSucursalEntrega> entregaRoot = subqueryEntrega.from(com.franco.dev.domain.operaciones.PedidoSucursalEntrega.class);
            subqueryEntrega.select(entregaRoot.get("pedido").get("id"));
            subqueryEntrega.where(cb.equal(entregaRoot.get("sucursal").get("id"), sucursalId));
            
            // Subquery para PedidoSucursalInfluencia
            Subquery<Long> subqueryInfluencia = query.subquery(Long.class);
            Root<com.franco.dev.domain.operaciones.PedidoSucursalInfluencia> influenciaRoot = subqueryInfluencia.from(com.franco.dev.domain.operaciones.PedidoSucursalInfluencia.class);
            subqueryInfluencia.select(influenciaRoot.get("pedido").get("id"));
            subqueryInfluencia.where(cb.equal(influenciaRoot.get("sucursal").get("id"), sucursalId));
            
            // Combinar ambas subqueries con OR usando union
            Predicate entregaPredicate = cb.in(root.get("id")).value(subqueryEntrega);
            Predicate influenciaPredicate = cb.in(root.get("id")).value(subqueryInfluencia);
            predicates.add(cb.or(entregaPredicate, influenciaPredicate));
            query.distinct(true);
        }
        
        // Filtro por estado (tipo de etapa actual) - usar subquery desde ProcesoEtapa
        if (estado != null && !estado.isEmpty()) {
            try {
                ProcesoEtapaTipo tipoEtapa = ProcesoEtapaTipo.valueOf(estado);
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<ProcesoEtapa> procesoEtapaRoot = subquery.from(ProcesoEtapa.class);
                subquery.select(procesoEtapaRoot.get("pedido").get("id"));
                subquery.where(cb.and(
                    cb.equal(procesoEtapaRoot.get("tipoEtapa"), tipoEtapa),
                    cb.equal(procesoEtapaRoot.get("estadoEtapa"), ProcesoEtapaEstado.COMPLETADA)
                ));
                predicates.add(cb.in(root.get("id")).value(subquery));
                query.distinct(true);
            } catch (IllegalArgumentException e) {
                // Si el estado no es válido, ignorar el filtro
                System.err.println("Estado de etapa no válido: " + estado);
            }
        }
        
        // Aplicar todos los predicados
        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(new Predicate[0]));
        }
        
        // Ordenar por id descendente
        query.orderBy(cb.desc(root.get("id")));
        
        // Ejecutar query con paginación
        TypedQuery<Pedido> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Pedido> content = typedQuery.getResultList();
        
        // Contar total de resultados para paginación
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Pedido> countRoot = countQuery.from(Pedido.class);
        countQuery.select(cb.countDistinct(countRoot));
        
        // Reconstruir predicados para el count query
        List<Predicate> countPredicates = new ArrayList<>();
        
        if (proveedorId != null) {
            countPredicates.add(cb.equal(countRoot.get("proveedor").get("id"), proveedorId));
        }
        
        if (creadoDesde != null) {
            countPredicates.add(cb.greaterThanOrEqualTo(countRoot.get("creadoEn"), creadoDesde));
        }
        
        if (creadoHasta != null) {
            countPredicates.add(cb.lessThanOrEqualTo(countRoot.get("creadoEn"), creadoHasta));
        }
        
        if (productoId != null) {
            Subquery<Long> subquery = countQuery.subquery(Long.class);
            Root<com.franco.dev.domain.operaciones.PedidoItem> pedidoItemRoot = subquery.from(com.franco.dev.domain.operaciones.PedidoItem.class);
            subquery.select(pedidoItemRoot.get("pedido").get("id"));
            subquery.where(cb.equal(pedidoItemRoot.get("producto").get("id"), productoId));
            countPredicates.add(cb.in(countRoot.get("id")).value(subquery));
        }
        
        if (sucursalId != null) {
            // Subquery para PedidoSucursalEntrega
            Subquery<Long> subqueryEntrega = countQuery.subquery(Long.class);
            Root<com.franco.dev.domain.operaciones.PedidoSucursalEntrega> entregaRoot = subqueryEntrega.from(com.franco.dev.domain.operaciones.PedidoSucursalEntrega.class);
            subqueryEntrega.select(entregaRoot.get("pedido").get("id"));
            subqueryEntrega.where(cb.equal(entregaRoot.get("sucursal").get("id"), sucursalId));
            
            // Subquery para PedidoSucursalInfluencia
            Subquery<Long> subqueryInfluencia = countQuery.subquery(Long.class);
            Root<com.franco.dev.domain.operaciones.PedidoSucursalInfluencia> influenciaRoot = subqueryInfluencia.from(com.franco.dev.domain.operaciones.PedidoSucursalInfluencia.class);
            subqueryInfluencia.select(influenciaRoot.get("pedido").get("id"));
            subqueryInfluencia.where(cb.equal(influenciaRoot.get("sucursal").get("id"), sucursalId));
            
            // Combinar ambas subqueries con OR
            Predicate entregaPredicate = cb.in(countRoot.get("id")).value(subqueryEntrega);
            Predicate influenciaPredicate = cb.in(countRoot.get("id")).value(subqueryInfluencia);
            countPredicates.add(cb.or(entregaPredicate, influenciaPredicate));
        }
        
        if (estado != null && !estado.isEmpty()) {
            try {
                ProcesoEtapaTipo tipoEtapa = ProcesoEtapaTipo.valueOf(estado);
                Subquery<Long> subquery = countQuery.subquery(Long.class);
                Root<ProcesoEtapa> procesoEtapaRoot = subquery.from(ProcesoEtapa.class);
                subquery.select(procesoEtapaRoot.get("pedido").get("id"));
                subquery.where(cb.and(
                    cb.equal(procesoEtapaRoot.get("tipoEtapa"), tipoEtapa),
                    cb.equal(procesoEtapaRoot.get("estadoEtapa"), ProcesoEtapaEstado.COMPLETADA)
                ));
                countPredicates.add(cb.in(countRoot.get("id")).value(subquery));
            } catch (IllegalArgumentException e) {
                // Ignorar si el estado no es válido
            }
        }
        
        if (!countPredicates.isEmpty()) {
            countQuery.where(countPredicates.toArray(new Predicate[0]));
        }
        
        Long totalElements = entityManager.createQuery(countQuery).getSingleResult();
        
        // Crear objeto Page
        return new PageImpl<>(content, pageable, totalElements);
    }
}