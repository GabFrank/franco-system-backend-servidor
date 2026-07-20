package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.productos.input.CodigoInput;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.repository.productos.CodigoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.search.ProductoSearchIndexSyncService;
import com.franco.dev.utilitarios.Ean13Utils;
import graphql.GraphQLException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import java.util.List;

import org.hibernate.Session;

@Service
public class CodigoService extends CrudService<Codigo, CodigoRepository, Long> {

    @Autowired
    private CodigoRepository repository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private TipoPrecioService tipoPrecioService;
    @Autowired
    private com.franco.dev.service.configuraciones.ModificacionService modificacionService;

    @Autowired
    private ProductoSearchIndexSyncService productoSearchIndexSyncService;

    @Override
    public CodigoRepository getRepository() {
        return repository;
    }

    @Autowired
    private Environment env;

    @PersistenceContext
    private EntityManager entityManager;

    private static final int GENERAR_MAX_INTENTOS = 50;

    // funcion para buscar un Codigo por su codigo de barra
    public List<Codigo> findByCodigo(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findByCodigo(texto.toUpperCase());
    }

    /**
     * Próximo EAN-13 interno libre (prefijo {@link Ean13Utils#INTERNAL_PREFIX}).
     * No persiste el registro; evita colisiones con UNIQUE(codigo).
     */
    @Transactional
    public String generarCodigoInterno() {
        // Lock de transacción via JDBC: pg_advisory_xact_lock retorna void (JDBC 1111)
        // y Hibernate no puede mapearlo con createNativeQuery(...).getSingleResult().
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?)")) {
                ps.setLong(1, 87219901L);
                ps.execute();
            }
        });

        Long maxSeq = repository.findMaxInternalEan21Sequence();
        long next = (maxSeq == null ? 0L : maxSeq) + 1;

        for (int i = 0; i < GENERAR_MAX_INTENTOS; i++) {
            String candidato = Ean13Utils.fromInternalSequence(next + i);
            if (!repository.existsByCodigo(candidato)) {
                return candidato;
            }
        }
        throw new GraphQLException("No se pudo generar un código interno único. Intente de nuevo.");
    }

    public Codigo save(CodigoInput input) throws GraphQLException {
        Codigo p = null;
        ModelMapper m = new ModelMapper();
        Codigo e = m.map(input, Codigo.class);
        if (e.getCodigo() != null)
            e.setCodigo(e.getCodigo().toUpperCase());
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getPresentacionId() != null)
            e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));

        // Obtener entidad anterior para comparar cambios (si es actualización)
        // IMPORTANTE: Obtener ANTES de guardar para tener los valores anteriores
        Codigo entidadAnterior = null;
        boolean esNuevo = (e.getId() == null);
        if (!esNuevo) {
            java.util.Optional<Codigo> codigoOpt = repository.findById(e.getId());
            if (codigoOpt != null && codigoOpt.isPresent()) {
                entidadAnterior = codigoOpt.get();
            }
        }

        p = repository.save(e);
        repository.flush(); // Asegurar que se guarde antes de registrar la modificación

        // Registrar modificación sin afectar la lógica existente
        try {
            if (esNuevo) {
                // Es una inserción
                modificacionService.registrarInsercion(p, "CODIGO", "productos", "codigo");
            } else if (entidadAnterior != null) {
                // Es una actualización
                modificacionService.registrarActualizacion(entidadAnterior, p, "CODIGO", "productos", "codigo");
            }
        } catch (Exception ex) {
            // No interrumpir el flujo si falla el registro de modificación
        }

        productoSearchIndexSyncService.sincronizarCodigo(p.getId());
        return p;
    }

    @Override
    @javax.transaction.Transactional
    public Boolean deleteById(Long id) {
        try {
            // Obtener entidad antes de eliminar para registrar la modificación
            Codigo entidad = repository.findById(id).orElse(null);
            if (entidad != null) {
                productoSearchIndexSyncService.eliminarCodigo(id);
                Boolean resultado = super.deleteById(id);
                // Registrar eliminación sin afectar la lógica existente
                try {
                    modificacionService.registrarEliminacion(entidad, "CODIGO", "productos", "codigo");
                } catch (Exception ex) {
                    // No interrumpir el flujo si falla el registro de modificación
                }
                return resultado;
            }
            return super.deleteById(id);
        } catch (Exception e) {
            return false;
        }
    }

    // @Override
    // public Boolean deleteById(Long id) {
    // Long idCentral = findById(id).get().getIdCentral();
    // if(idCentral!=null) {
    // propagarDelete(idCentral);
    // return super.deleteById(id);
    // }
    // return false;
    // }

    // public String propagar(CodigoInput input){
    // log.info("enviando codigo a central");
    // log.info(input.toString());
    // RabbitDto<CodigoInput> dto = new RabbitDto();
    // dto.setAccion(GUARDAR);
    //// dto.setTipo(Receiver.CODIGO);
    // dto.setEntidad(input);
    //// sender.send(dto, "central");
    // return "Success";
    // }

    // public String propagarDelete(Long idCentral){
    // log.info("enviando delete codigo a central");
    // log.info(idCentral.toString());
    // RabbitDto<CodigoInput> dto = new RabbitDto();
    // dto.setAccion(ELIMINAR);
    //// dto.setTipo(Receiver.CODIGO);
    // CodigoInput input = new CodigoInput();
    // input.setIdCentral(idCentral);
    // dto.setEntidad(input);
    //// sender.send(dto, "central");
    // return "Success";
    // }
    //
    // public void receive(RabbitDto<CodigoInput> dto) {
    // log.info("recibiendo codigo");
    // log.info("accion: " + dto.getAccion());
    // switch (dto.getAccion()){
    // case GUARDAR:
    // save(dto.getEntidad());
    // case ELIMINAR:
    // deleteById(dto.getEntidad().getIdCentral());
    // case ACTUALIZAR:
    // }
    // }

    public List<Codigo> findByPresentacionId(Long id) {
        return repository.findByPresentacionId(id);
    }

    public Codigo findPrincipalByPresentacionId(Long id) {
        return repository.findPrincipalByPresentacionId(id);
    }

}
