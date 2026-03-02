package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.domain.personas.Persona;
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

    public List<Role> getRoles(Long id) {
        List<UsuarioRole> usuarioRoleList = usuarioRoleService.findByUserId(id);
        List<Role> roleList = new ArrayList<Role>();
        if (!usuarioRoleList.isEmpty()) {
            usuarioRoleList.forEach(usuarioRole -> {
                Role role = roleService.findById(usuarioRole.getUser().getId()).orElse(null);
                if (role != null) {
                    roleList.add(role);
                }
            });
        }
        return roleList;
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

    public Boolean saveUserImage(Long id, String type, String image, List<Double> embedding) throws IOException {
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
                if (embedding != null && !embedding.isEmpty()) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        persona.setEmbedding(mapper.writeValueAsString(embedding));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                personaRepository.save(persona);
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
            // Si ya tiene embedding o ya tiene imagenes de perfil, consideramos que tiene
            // registro facial
            if ((usuario.getPersona().getEmbedding() != null && !usuario.getPersona().getEmbedding().isEmpty()) ||
                    (usuario.getPersona().getImagenes() != null && !usuario.getPersona().getImagenes().isEmpty())) {
                return 3;
            }
        }
        List<String> images = getUserImages(id, "auth");
        return images.size();
    }

    public com.franco.dev.graphql.personas.UsuarioSimilitudResult findUsuarioByEmbedding(List<Double> embeddingInfo,
            List<Integer> excludeIds) {
        List<Usuario> usuarios = repository.findAllActivos();
        Usuario bestMatch = null;
        Double maxSimilarity = -1.0;

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (Usuario usuario : usuarios) {
            if (excludeIds != null && !excludeIds.isEmpty() && excludeIds.contains(usuario.getId().intValue())) {
                continue;
            }

            if (usuario.getPersona() != null && usuario.getPersona().getEmbedding() != null) {
                try {
                    List<Double> storedEmbedding = mapper.readValue(usuario.getPersona().getEmbedding(),
                            new com.fasterxml.jackson.core.type.TypeReference<List<Double>>() {
                            });

                    Double similarity = cosineSimilarity(embeddingInfo, storedEmbedding);

                    if (similarity > maxSimilarity) {
                        maxSimilarity = similarity;
                        bestMatch = usuario;
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing embedding for user " + usuario.getId() + ": " + e.getMessage());
                }
            }
        }

        if (bestMatch != null) {
            if (maxSimilarity > 0.75) {
                System.out
                        .println("Best match found: " + bestMatch.getNickname() + " with similarity: " + maxSimilarity);
                return new com.franco.dev.graphql.personas.UsuarioSimilitudResult(bestMatch, maxSimilarity);
            } else {
                System.out.println("No match found. Best candidate: " + bestMatch.getNickname() + " with similarity: "
                        + maxSimilarity + " (Threshold: 0.75)");
                return null;
            }
        }

        System.out.println("No match found. Excluded IDs: " + (excludeIds != null ? excludeIds : "none"));
        return null;
    }

    private Double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normA += Math.pow(v1.get(i), 2);
            normB += Math.pow(v2.get(i), 2);
        }

        if (normA == 0 || normB == 0)
            return 0.0;

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
