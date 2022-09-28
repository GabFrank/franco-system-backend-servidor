package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.repository.personas.RoleRepository;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    @Override
    public UsuarioRepository getRepository() {
        return repository;
    }

    public Usuario findByPersonaId(Long id){ return repository.findByPersonaId(id);}


    public List<Usuario> findbyIdOrPersona(String texto) {
        texto = texto.replace(' ', '%');
        return repository.findbyIdOrPersona(texto.toUpperCase());
    }

    public List<Role> getRoles(Long id){
        List<UsuarioRole> usuarioRoleList = usuarioRoleService.findByUserId(id);
        List<Role> roleList = new ArrayList<Role>();
        if(!usuarioRoleList.isEmpty()){
            usuarioRoleList.forEach(usuarioRole -> {
                Role role = roleService.findById(usuarioRole.getUser().getId()).orElse(null);
                if(role!=null){
                    roleList.add(role);
                }
            });
        }
        return roleList;
    }

    public Usuario findByEmail(String email){
        return repository.findByEmail(email).orElse(null);
    }

    public Boolean existsByEmail(String email){
        return repository.existsByEmail(email);
    }

    public Boolean existsByNickname(String nickname){
        return repository.existsByNicknameIgnoreCase(nickname);
    }

    public Optional<Usuario> findByNickname(String nickname){
        return repository.findByNicknameIgnoreCase(nickname.toUpperCase());
    }

    @Override
    public Usuario save(Usuario entity) {
        if(entity.getId()==null){
            entity.setCreadoEn(LocalDateTime.now());
            entity.setPassword("123");
        }
        entity.setNickname(entity.getNickname().toUpperCase());
        if(entity.getPassword()!=null) entity.setPassword(entity.getPassword().toUpperCase());
        Usuario e = repository.save(entity);
        return e;
    }
}
