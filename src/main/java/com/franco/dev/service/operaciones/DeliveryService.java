package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.VentaPorSucursal;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.graphql.operaciones.publisher.DeliveryPublisher;
import com.franco.dev.graphql.personas.publisher.PersonaPublisher;
import com.franco.dev.repository.operaciones.DeliveryRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.ajustarFinRangoGrafico;
import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Service
@AllArgsConstructor
public class DeliveryService extends CrudService<Delivery, DeliveryRepository, EmbebedPrimaryKey> {
    private final DeliveryRepository repository;
    private final DeliveryPublisher deliveryPublisher;

    @Override
    public DeliveryRepository getRepository() {
        return repository;
    }

    public List<Delivery> findByEstado(DeliveryEstado estado){
        return  repository.findByEstado(estado);
    }

    public List<Delivery> findByEstadoNotIn(DeliveryEstado estado){
        return  repository.findActivos();
    }

    public List<Delivery> findTop10(){
        return repository.findUltimos10();
    }

    public Delivery findByIdAndSucursalId(Long id, Long sucId){
        return repository.findByIdAndSucursalId(id, sucId);
    }

    /**
     * Total facturado en delivery por sucursal en un rango (gráfico "Ventas con Delivery").
     * Reutiliza el DTO {@link VentaPorSucursal}: {@code cantidadVentas} = cantidad de deliveries concluidos.
     */
    public List<VentaPorSucursal> deliveryPorSucursal(String fechaInicio, String fechaFin) {
        LocalDateTime inicio = stringToDate(fechaInicio);
        LocalDateTime fin = ajustarFinRangoGrafico(fechaFin, stringToDate(fechaFin));
        List<Object[]> results = repository.findTotalDeliveryPorSucursal(inicio, fin);
        List<VentaPorSucursal> list = new ArrayList<>();
        for (Object[] obj : results) {
            VentaPorSucursal dto = new VentaPorSucursal();
            dto.setSucId(obj[0] != null ? ((Number) obj[0]).longValue() : null);
            dto.setNombre(obj[1] != null ? String.valueOf(obj[1]) : "");
            dto.setTotal(obj[2] != null ? ((Number) obj[2]).doubleValue() : 0.0);
            dto.setCantidadVentas(obj[3] != null ? ((Number) obj[3]).doubleValue() : 0.0);
            list.add(dto);
        }
        return list;
    }

    @Override
    public Delivery save(Delivery entity) {
        Delivery e = super.save(entity);
        deliveryPublisher.publish(e);
        return e;
    }

}