package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaObservacion;
import com.franco.dev.graphql.operaciones.input.VentaObservacionInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.MotivoObservacionService;
import com.franco.dev.service.operaciones.SubcategoriaObservacionService;
import com.franco.dev.service.operaciones.VentaObservacionService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VentaObservacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private com.franco.dev.service.financiero.TesoreriaSecurityService seg;

    @Autowired
    private VentaObservacionService service;

    @Autowired
    private MotivoObservacionService motivoObservacionService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private UsuarioService usuarioService;

    public VentaObservacion ventaObservacion(Long ventaId) {
        return service.findById(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("VentaObservacion not found for id: " + ventaId));
    }

    public List<VentaObservacion> findByVentaIdAndSucursalId(Long ventaId, Long sucursalId) {
        return service.findByVentaIdAndSucursalId(ventaId, sucursalId);
    }

    public List<VentaObservacion> ventaObservaciones() { return service.findAll2();}

    public VentaObservacion saveVentaObservacion(VentaObservacionInput input) {
        seg.requireAnyRole("ANALISIS DE CAJA", "ANALISIS DE VENTA");
        ModelMapper m = new ModelMapper();
        VentaObservacion e = m.map(input, VentaObservacion.class);
        if (input.getMotivoObservacionId() != null)
            e.setMotivoObservacion(motivoObservacionService.findById(input.getMotivoObservacionId()).orElse(null));
        if (input.getVentaId() != null && input.getSucursalId() != null) {

            Venta venta = ventaService.findByIdAndSucursalId(input.getVentaId(), input.getSucursalId());
            if (venta == null) {
                throw new IllegalArgumentException(
                        "Venta not found for ventaId: " + input.getVentaId()
                        + " and sucursalId: " + input.getSucursalId());
            }
            e.setVenta(venta);
        }
        if (input.getSucursalId() != null)
            e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        // el autor es el usuario de la sesion; al editar se conserva el original
        VentaObservacion previa = input.getId() != null ? service.findById(input.getId()).orElse(null) : null;
        e.setUsuario(previa != null ? previa.getUsuario() : seg.requireUsuarioDeSesion());
        return service.save(e);
    }

    public Boolean deleteVentaObservacion(Long id) {
        seg.requireSuperusuario(); // sin pantalla que la llame
        Boolean ok = service.deleteById(id);
        return ok;
    }
}
