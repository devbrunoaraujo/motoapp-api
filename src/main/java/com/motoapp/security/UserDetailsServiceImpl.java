package com.motoapp.security;

// =============================================================================
// IMPORTS
// =============================================================================
import com.motoapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// =============================================================================
// O PAPEL DESTA CLASSE NO SPRING SECURITY
// =============================================================================
//
// Quando o Spring Security precisa autenticar alguém, ele segue este fluxo:
//
//   Requisição HTTP com credenciais
//         ↓
//   AuthenticationManager
//         ↓
//   DaoAuthenticationProvider      ← verifica a senha com BCrypt
//         ↓
//   UserDetailsService.loadUserByUsername(email)  ← ESTA CLASSE
//         ↓
//   UserDetails (nossa Entity User)
//         ↓
//   Token JWT gerado e retornado
//
// UserDetailsService é uma interface do Spring Security com UM único método:
//   UserDetails loadUserByUsername(String username)
//
// "username" no Spring Security = identificador único do usuário.
// No nosso sistema, usamos o EMAIL como identificador (não um username literal).
// O Spring não sabe disso — é por isso que precisamos desta implementação:
// para dizer "quando você pedir pelo username, busque pelo EMAIL no banco".
//
// =============================================================================

// =============================================================================
// @RequiredArgsConstructor — Injeção de dependência via construtor
// =============================================================================
//
// Esta anotação do Lombok gera um construtor com todos os campos "final".
// O Spring usa esse construtor para injetar as dependências automaticamente.
//
// É preferível à @Autowired em campo porque:
// 1. Imutabilidade: campos final não podem ser reatribuídos após injeção
// 2. Testabilidade: no teste, você passa o mock pelo construtor
// 3. Detecta dependências circulares em tempo de inicialização (não em runtime)
// 4. É a recomendação oficial do Spring e da comunidade Java
//
// Equivalente manual (sem Lombok):
//   public UserDetailsServiceImpl(UserRepository userRepository) {
//       this.userRepository = userRepository;
//   }
// =============================================================================
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    // final → o Lombok inclui no construtor gerado pelo @RequiredArgsConstructor
    // O Spring injeta o UserRepository automaticamente neste construtor
    private final UserRepository userRepository;

    // =========================================================================
    // loadUserByUsername — O único método da interface UserDetailsService
    // =========================================================================
    //
    // @Transactional(readOnly = true)
    //   readOnly = true → otimização para operações de leitura:
    //   - O banco de dados pode usar réplicas de leitura
    //   - O Hibernate desativa o dirty checking (verificação de mudanças)
    //   - Melhora performance em até 20-30% em operações de SELECT puro
    //   - Se tentar fazer um INSERT/UPDATE dentro, lança exceção
    //
    // Sempre anote métodos que só leem com @Transactional(readOnly = true).
    // Métodos que escrevem usam @Transactional sem parâmetros (padrão = readOnly=false).
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        // "username" aqui é o EMAIL — convenção do Spring Security.
        // findByEmail busca no banco e retorna Optional<User>.
        //
        // orElseThrow → se o Optional estiver vazio (email não encontrado),
        //               lança UsernameNotFoundException com mensagem descritiva.
        //
        // Por que não retornar null?
        // O contrato da interface proíbe retornar null — lança NPE no Spring.
        // A exceção correta é UsernameNotFoundException.
        //
        // SEGURANÇA: a mensagem "Usuário não encontrado" é genérica de propósito.
        // NÃO diga "email não cadastrado" vs "senha incorreta" — isso permite
        // que atacantes descubram quais emails estão cadastrados (user enumeration).
        // Sempre use a mesma mensagem genérica para ambos os casos.
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado: " + username
                ));

        // Por que retornamos User diretamente como UserDetails?
        // Nossa Entity User implementa a interface UserDetails (vimos no Arquivo 4).
        // O Spring Security aceita qualquer objeto que implemente UserDetails —
        // não precisa de um objeto separado. Essa foi nossa decisão de design:
        // manter tudo em uma classe só para simplificar.
    }
}