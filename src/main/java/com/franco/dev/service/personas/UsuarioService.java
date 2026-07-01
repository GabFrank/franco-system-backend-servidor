package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.domain.personas.enums.ResultadoIncorporacionEmbedding;
import com.franco.dev.graphql.personas.IncorporarEmbeddingMarcacionResult;
import com.franco.dev.service.personas.dto.IncorporarCapturaMarcacionResult;
import com.franco.dev.repository.personas.PersonaRepository;
import com.franco.dev.repository.personas.RoleRepository;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.utils.ImageService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class UsuarioService extends CrudService<Usuario, UsuarioRepository, Long> {

    @Autowired
    private final UsuarioRepository repository;

    @Autowired
    private final RoleRepository roleRepository;

    @Autowired
    private final UsuarioRoleService usuarioRoleService;

    @Autowired
    private final RoleService roleService;

    @Autowired
    private final PersonaRepository personaRepository;

    @Autowired
    private final ImageService imageService;

    @Autowired
    private final UsuarioEmbeddingCacheService embeddingCacheService;

    @Autowired
    private final EmbeddingGaleriaService embeddingGaleriaService;

    @Override
    public UsuarioRepository getRepository() {
        return repository;
    }

    public Usuario findByPersonaId(Long id) {
        return repository.findByPersonaId(id);
    }

    public List<Usuario> findbyIdOrPersona(String texto) {
        texto = texto != null ? texto.trim() : "";

        if (!texto.isEmpty() && texto.chars().allMatch(Character::isDigit)) {
            try {
                Long personaId = Long.valueOf(texto);
                Usuario usuario = repository.findByPersonaId(personaId);
                if (usuario != null) {
                    List<Usuario> resultado = new ArrayList<>();
                    resultado.add(usuario);
                    return resultado;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        texto = texto.replace(' ', '%');
        return repository.findbyIdOrPersona(texto.toUpperCase());
    }

    public org.springframework.data.domain.Page<Usuario> findbyIdOrPersonaPaginated(String texto, Integer page, Integer size) {
        if (texto == null) texto = "";
        if (page == null) page = 0;
        if (size == null) size = 15;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return repository.findbyIdOrPersonaPaginated(texto.toUpperCase(), pageable);
    }

    public List<Role> getRoles(Long id) {
        List<UsuarioRole> usuarioRoleList = usuarioRoleService.findByUserId(id);
        List<Role> roleList = new ArrayList<Role>();
        if (!usuarioRoleList.isEmpty()) {
            usuarioRoleList.forEach(usuarioRole -> {
                Role role = usuarioRole.getRole();
                if (role != null) {
                    roleList.add(role);
                }
            });
        }
        return roleList;
    }

    public boolean tieneRol(Long usuarioId, String... nombresRol) {
        if (usuarioId == null || nombresRol == null || nombresRol.length == 0) return false;
        List<Role> roles = getRoles(usuarioId);
        return roles.stream().anyMatch(r ->
            java.util.Arrays.stream(nombresRol)
                .anyMatch(nombre -> nombre.equalsIgnoreCase(r.getNombre()))
        );
    }

    public Usuario findByEmail(String email) {
        return repository.findByEmail(email).orElse(null);
    }

    public Boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    public Boolean existsByNickname(String nickname) {
        return repository.existsByNicknameIgnoreCase(nickname);
    }

    public Optional<Usuario> findByNickname(String nickname) {
        if (nickname == null) {
            return Optional.empty();
        }
        return repository.findByNicknameIgnoreCase(nickname.toUpperCase());
    }

    /**
     * Obtiene todos los usuarios activos ordenados por nombre
     * 
     * @return Lista de usuarios activos
     */
    public List<Usuario> findAllActivos() {
        return repository.findAllActivos();
    }

    @Override
    public Usuario save(Usuario entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setPassword("123");
        }
        entity.setNickname(entity.getNickname().toUpperCase());
        if (entity.getPassword() != null)
            entity.setPassword(entity.getPassword().toUpperCase());
        Usuario e = repository.save(entity);
        return e;
    }

    public Boolean saveUserImage(Long id, String type, String image, List<Double> embedding, String embeddingGaleriaJson)
            throws IOException {
        System.out.println("Saving user image for id: " + id + ", type: " + type);
        try {
            String directoryPath = imageService.getImagePath() + File.separator + "personas" + File.separator + type
                    + File.separator;
            File dir = new File(directoryPath);
            if (dir.exists() && dir.isDirectory()) {
                File[] existingFiles = dir.listFiles((d, name) -> name.startsWith(id + "_" + type));
                if (existingFiles != null) {
                    for (File file : existingFiles) {
                        System.out.println("Deleting old image: " + file.getName());
                        file.delete();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String fileName = id + "_" + type + System.currentTimeMillis() + ".png";
        Boolean saved = imageService.saveImageToPath(image, fileName,
                imageService.getImagePath() + File.separator + "personas" + File.separator + type + File.separator,
                imageService.getImagePath() + File.separator + "personas" + File.separator + type + File.separator
                        + "thumb",
                true);
        if (saved) {
            Usuario usuario = repository.findById(id).orElse(null);
            if (usuario != null && usuario.getPersona() != null) {
                Persona persona = usuario.getPersona();
                persona.setImagenes(fileName);
                String embeddingJson = resolverEmbeddingJson(embeddingGaleriaJson);
                if (embeddingJson != null) {
                    persona.setEmbedding(embeddingJson);
                }
                personaRepository.saveAndFlush(persona);
                if (embeddingJson != null) {
                    embeddingCacheService.refreshUsuario(id, embeddingJson);
                }
            }
        }
        return saved;
    }

    public List<String> getUserImages(Long id, String type) {
        return imageService.getImages(id + "_" + type, "personas" + File.separator + type, true);
    }

    public Integer isUserFaceAuth(Long id) {
        Usuario usuario = repository.findById(id).orElse(null);
        if (usuario != null && usuario.getPersona() != null) {
            String embeddingJson = usuario.getPersona().getEmbedding();
            if (embeddingJson != null && !embeddingJson.isEmpty()) {
                com.franco.dev.graphql.personas.dto.EmbeddingGaleriaDto galeria = embeddingGaleriaService
                        .parsearDesdeJson(embeddingJson);
                if (galeria != null && galeria.getGallery() != null && !galeria.getGallery().isEmpty()) {
                    return 3;
                }
            }
        }
        List<String> images = getUserImages(id, "auth");
        return images.size();
    }

    public com.franco.dev.graphql.personas.UsuarioSimilitudResult findUsuarioByEmbedding(List<Double> embeddingInfo,
            List<Integer> excludeIds) {
        return embeddingCacheService.findBestMatch(embeddingInfo, excludeIds);
    }

    public IncorporarEmbeddingMarcacionResult incorporarEmbeddingMarcacion(Long usuarioId, List<Double> embedding, Double score) {
        if (usuarioId == null || embedding == null || embedding.isEmpty()) {
            return new IncorporarEmbeddingMarcacionResult(
                    ResultadoIncorporacionEmbedding.RECHAZADO_SCORE,
                    "Datos de captura incompletos para actualizar el perfil facial.");
        }
        Usuario usuario = repository.findById(usuarioId).orElse(null);
        if (usuario == null || usuario.getPersona() == null) {
            return new IncorporarEmbeddingMarcacionResult(
                    ResultadoIncorporacionEmbedding.RECHAZADO_SIMILITUD,
                    "No se encontró galería facial válida para actualizar.");
        }
        String jsonActual = usuario.getPersona().getEmbedding();
        com.franco.dev.graphql.personas.dto.EmbeddingGaleriaDto galeria = embeddingGaleriaService.parsearDesdeJson(jsonActual);
        if (galeria == null || galeria.getGallery() == null || galeria.getGallery().isEmpty()) {
            return new IncorporarEmbeddingMarcacionResult(
                    ResultadoIncorporacionEmbedding.RECHAZADO_SIMILITUD,
                    "No se encontró galería facial válida para actualizar.");
        }

        IncorporarCapturaMarcacionResult resultado = embeddingGaleriaService
                .incorporarCapturaMarcacion(galeria, embedding, score);
        if (resultado.getResultado() != ResultadoIncorporacionEmbedding.OK) {
            return new IncorporarEmbeddingMarcacionResult(resultado.getResultado(), resultado.getMensaje());
        }

        String embeddingJson = embeddingGaleriaService.serializar(resultado.getGaleria());
        if (embeddingJson == null) {
            return new IncorporarEmbeddingMarcacionResult(
                    ResultadoIncorporacionEmbedding.RECHAZADO_SIMILITUD,
                    "No se pudo guardar la galería facial actualizada.");
        }

        Persona persona = usuario.getPersona();
        persona.setEmbedding(embeddingJson);
        personaRepository.saveAndFlush(persona);
        embeddingCacheService.refreshUsuario(usuarioId, embeddingJson);
        return new IncorporarEmbeddingMarcacionResult(resultado.getResultado(), resultado.getMensaje());
    }

    private String resolverEmbeddingJson(String embeddingGaleriaJson) {
        if (embeddingGaleriaJson == null || embeddingGaleriaJson.isBlank()) {
            return null;
        }
        return embeddingGaleriaService.normalizarJsonEntrada(embeddingGaleriaJson);
    }
}
