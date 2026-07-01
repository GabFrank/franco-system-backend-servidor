package com.franco.dev.service.equipos;

import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.domain.equipos.Equipo;
import com.franco.dev.domain.equipos.EquipoFinanciero;
import com.franco.dev.graphql.equipos.dto.EquipoOutput;
import com.franco.dev.graphql.equipos.input.EquipoFinancieroInput;
import com.franco.dev.graphql.equipos.input.EquipoInput;
import com.franco.dev.repository.equipos.EquipoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.ActivoFinancieroSyncFacade;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EquipoService extends CrudService<Equipo, EquipoRepository, Long> {

    private final EquipoRepository repository;
    private final EnteService enteService;
    private final PersonaService personaService;
    private final TipoEquipoService tipoEquipoService;
    private final ModeloEquipoService modeloEquipoService;
    private final EquipoFinancieroService equipoFinancieroService;
    private final SucursalService sucursalService;
    private final UsuarioService usuarioService;
    private final ActivoFinancieroSyncFacade activoFinancieroSyncFacade;

    public EquipoService(
            EquipoRepository repository,
            @Lazy EnteService enteService,
            PersonaService personaService,
            TipoEquipoService tipoEquipoService,
            ModeloEquipoService modeloEquipoService,
            EquipoFinancieroService equipoFinancieroService,
            SucursalService sucursalService,
            UsuarioService usuarioService,
            ActivoFinancieroSyncFacade activoFinancieroSyncFacade) {
        this.repository = repository;
        this.enteService = enteService;
        this.personaService = personaService;
        this.tipoEquipoService = tipoEquipoService;
        this.modeloEquipoService = modeloEquipoService;
        this.equipoFinancieroService = equipoFinancieroService;
        this.sucursalService = sucursalService;
        this.usuarioService = usuarioService;
        this.activoFinancieroSyncFacade = activoFinancieroSyncFacade;
    }

    @Override
    public EquipoRepository getRepository() {
        return repository;
    }

    public List<EquipoOutput> buscar(String texto) {
        if (texto == null) {
            texto = "";
        }
        texto = texto.replace(' ', '%');
        return repository.findByAll(texto.toUpperCase()).stream()
                .map(this::aOutput)
                .collect(Collectors.toList());
    }

    public Page<EquipoOutput> buscarConPagina(String texto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        texto = texto != null ? texto.replace(' ', '%').toUpperCase() : "";
        return repository.findByAllWithPage(texto, pageable).map(this::aOutput);
    }

    public List<EquipoOutput> buscarPorTipoEquipoId(Long tipoEquipoId) {
        return repository.findByTipoEquipoId(tipoEquipoId).stream()
                .map(this::aOutput)
                .collect(Collectors.toList());
    }

    public List<EquipoOutput> buscarPorPropietarioId(Long propietarioId) {
        return repository.findByPropietarioId(propietarioId).stream()
                .map(this::aOutput)
                .collect(Collectors.toList());
    }

    public EquipoFinanciero resolverFinanciero(Equipo entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getFinanciero() != null) {
            return entity.getFinanciero();
        }
        if (entity.getId() == null) {
            return null;
        }
        return equipoFinancieroService.buscarPorEquipoId(entity.getId()).orElse(null);
    }

    public EquipoOutput aOutput(Equipo entity) {
        if (entity == null) {
            return null;
        }
        EquipoFinanciero financiero = resolverFinanciero(entity);

        EquipoOutput output = new EquipoOutput();
        output.setId(entity.getId());
        output.setPropietario(entity.getPropietario());
        output.setIdentificador(entity.getIdentificador());
        output.setModelo(modeloEquipoService.aOutput(entity.getModelo()));
        output.setDescripcion(entity.getDescripcion());
        output.setImagenes(entity.getImagenes());
        output.setTipoEquipo(tipoEquipoService.aOutput(entity.getTipoEquipo()));
        output.setConsumeEnergia(entity.getConsumeEnergia());
        output.setConsumoValor(entity.getConsumoValor());
        output.setFinanciero(equipoFinancieroService.aOutput(financiero));
        output.setSucursal(entity.getSucursal());
        output.setUsuario(entity.getUsuario());
        output.setCreadoEn(entity.getCreadoEn());
        return output;
    }

    public EquipoOutput guardarDesdeInput(EquipoInput input) {
        Equipo entity = new Equipo();
        if (input.getId() != null) {
            entity = repository.findById(input.getId()).orElse(new Equipo());
            equipoFinancieroService.buscarPorEquipoId(entity.getId()).ifPresent(entity::setFinanciero);
        }

        entity.setIdentificador(aMayusculas(input.getIdentificador()));
        entity.setDescripcion(aMayusculas(input.getDescripcion()));
        if (input.getModeloId() != null) {
            entity.setModelo(modeloEquipoService.findById(input.getModeloId()).orElse(null));
        }
        entity.setImagenes(input.getImagenes());
        entity.setConsumeEnergia(input.getConsumeEnergia());
        entity.setConsumoValor(aMayusculas(input.getConsumoValor()));

        if (input.getPropietarioId() != null) {
            entity.setPropietario(personaService.findById(input.getPropietarioId()).orElse(null));
        }
        if (input.getTipoEquipoId() != null) {
            entity.setTipoEquipo(tipoEquipoService.findById(input.getTipoEquipoId()).orElse(null));
        }
        if (input.getSucursalId() != null) {
            entity.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }

        EquipoFinancieroInput financieroInput = input.getFinanciero() != null
                ? input.getFinanciero()
                : equipoFinancieroService.extraerInput(input);
        equipoFinancieroService.vincularAEquipo(entity, financieroInput);

        Equipo guardado = save(entity);
        activoFinancieroSyncFacade.sync(
                TipoEnte.EQUIPO,
                guardado.getId(),
                financieroInput.getSituacionPago(),
                financieroInput.getProveedorId(),
                financieroInput.getMonedaId(),
                financieroInput.getMontoTotal(),
                financieroInput.getMontoYaPagado(),
                financieroInput.getCantidadCuotas(),
                financieroInput.getCantidadCuotasPagadas(),
                financieroInput.getDiaVencimiento(),
                financieroInput.getCuotasDetalle(),
                financieroInput.getUsuarioId() != null ? financieroInput.getUsuarioId() : input.getUsuarioId()
        );
        return aOutput(guardado);
    }

    @Override
    public Equipo save(Equipo entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        Equipo guardado = super.save(entity);
        if (guardado != null) {
            String descripcion = guardado.getDescripcion() != null ? guardado.getDescripcion()
                    : (guardado.getIdentificador() != null ? guardado.getIdentificador()
                            : construirDescripcionPorDefecto(guardado));
            enteService.ensureEnteForReferencia(TipoEnte.EQUIPO, guardado.getId(), descripcion, guardado.getUsuario());
        }
        return guardado;
    }

    @Override
    public Boolean deleteById(Long id) {
        enteService.findByTipoEnteAndReferenciaId(TipoEnte.EQUIPO, id).ifPresent(ente -> enteService.deleteById(ente.getId()));
        return super.deleteById(id);
    }

    private String construirDescripcionPorDefecto(Equipo equipo) {
        if (equipo.getModelo() != null) {
            String marca = equipo.getModelo().getMarca() != null ? equipo.getModelo().getMarca().getDescripcion() : null;
            String modelo = equipo.getModelo().getDescripcion();
            if (marca != null && modelo != null) {
                return marca + " " + modelo;
            }
            if (modelo != null) {
                return modelo;
            }
        }
        return "Equipo #" + equipo.getId();
    }

    private String aMayusculas(String value) {
        return value != null ? value.toUpperCase() : null;
    }
}
