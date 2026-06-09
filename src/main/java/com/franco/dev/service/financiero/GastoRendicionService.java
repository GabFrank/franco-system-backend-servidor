package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.GastoRendicion;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.repository.financiero.GastoRendicionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.utils.ImageService;
import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GastoRendicionService extends CrudService<GastoRendicion, GastoRendicionRepository, Long> {

    private static final String RENDICION_IMAGE_FOLDER = "financiero" + File.separator + "gasto_rendicion";

    private final GastoRendicionRepository repository;
    private final PreGastoService preGastoService;
    private final ImageService imageService;

    @Override
    public GastoRendicionRepository getRepository() {
        return repository;
    }

    @Override
    @Transactional
    public GastoRendicion save(GastoRendicion entity) {
        if (entity.getCreadoEn() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        
        PreGasto preGasto = null;
        if (entity.getPreGasto() != null) {
            preGasto = preGastoService.findByIdAndSucursalId(
                    entity.getPreGasto().getId(), entity.getPreGasto().getSucursalId());
            if (preGasto != null) {
                entity.setPreGasto(preGasto);
                if (entity.getEnte() == null) {
                    entity.setEnte(preGasto.getEnte());
                }
                if (entity.getTipoGasto() == null && preGasto.getTipoGasto() != null) {
                    entity.setTipoGasto(preGasto.getTipoGasto());
                }
            }
        }

        if (preGasto != null) {
            entity.setFotoFacturaUrl(persistImage(
                    entity.getFotoFacturaUrl(), "factura", preGasto.getId(), preGasto.getSucursalId()));
            entity.setFotoProductoUrl(persistImage(
                    entity.getFotoProductoUrl(), "producto", preGasto.getId(), preGasto.getSucursalId()));
        }

        GastoRendicion saved = super.save(entity);
        
        // Actualizar el PreGasto asociado
        if (saved.getPreGasto() != null) {
            actualizarMontoPreGasto(saved.getPreGasto().getId(), saved.getPreGasto().getSucursalId());
        }
        
        return saved;
    }

    @Transactional
    public void actualizarMontoPreGasto(Long preGastoId, Long sucursalId) {
        PreGasto preGasto = preGastoService.findByIdAndSucursalId(preGastoId, sucursalId);
        if (preGasto != null) {
            List<GastoRendicion> rendiciones = repository.findByPreGastoIdAndSucursalId(preGastoId, sucursalId);
            BigDecimal totalGastado = rendiciones.stream()
                    .map(r -> r.getMontoTotal() != null ? r.getMontoTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            preGasto.setMontoGastado(totalGastado);
            BigDecimal montoRetirado = preGasto.getMontoRetirado() != null ? preGasto.getMontoRetirado() : BigDecimal.ZERO;
            preGasto.setSaldoDevolver(montoRetirado.subtract(totalGastado).max(BigDecimal.ZERO));
            if (totalGastado.compareTo(BigDecimal.ZERO) > 0) {
                preGasto.setFechaRendicion(LocalDateTime.now());
            }
            preGastoService.recalcularEstadoRendicionPublico(preGasto);
            preGastoService.save(preGasto);
        }
    }

    public List<GastoRendicion> findByPreGasto(Long preGastoId, Long sucursalId) {
        return repository.findByPreGastoIdAndSucursalId(preGastoId, sucursalId);
    }

    public String resolveImageAsDataUrl(String storedValue, String tipo) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        if (storedValue.startsWith("data:image")) {
            return storedValue;
        }
        File file = new File(getImageDirectory(tipo) + storedValue);
        if (!file.exists()) {
            return null;
        }
        return imageService.fileToBase64(file);
    }

    private String persistImage(String imageData, String tipo, Long preGastoId, Long sucursalId) {
        if (imageData == null || imageData.isBlank()) {
            return null;
        }
        if (!imageData.startsWith("data:image")) {
            return imageData;
        }
        String fileName = preGastoId + "_" + sucursalId + "_" + tipo + System.currentTimeMillis() + ".jpg";
        String directory = getImageDirectory(tipo);
        String thumbDirectory = directory + "thumb" + File.separator;
        try {
            Boolean saved = imageService.saveImageToPath(imageData, fileName, directory, thumbDirectory, true);
            if (!Boolean.TRUE.equals(saved)) {
                throw new GraphQLException("No se pudo guardar la imagen de la rendición.");
            }
            return fileName;
        } catch (IOException e) {
            throw new GraphQLException("No se pudo guardar la imagen de la rendición.");
        }
    }

    private String getImageDirectory(String tipo) {
        return imageService.getImagePath() + RENDICION_IMAGE_FOLDER + File.separator + tipo + File.separator;
    }
}
