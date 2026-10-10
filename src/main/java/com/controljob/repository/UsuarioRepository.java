package com.controljob.repository;

import com.controljob.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByNomeUsuario(String nomeUsuario);
    List<Usuario> findAllByOrderByNomeUsuarioAsc();
}
