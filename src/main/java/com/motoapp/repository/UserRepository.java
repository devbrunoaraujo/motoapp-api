package com.motoapp.repository;

// =============================================================================
// IMPORTS
// =============================================================================
import com.motoapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// =============================================================================
// @Repository — O que esta anotação faz?
// =============================================================================
//
// Marca esta interface como um componente da camada de persistência.
// O Spring a detecta no @ComponentScan e a registra como um bean.
//
// Tecnicamente, @Repository é opcional quando usamos JpaRepository
// (o Spring Data já registra automaticamente), mas a colocamos porque:
// 1. Documenta a intenção — fica claro que é um repositório de dados
// 2. Ativa a tradução de exceções do banco para DataAccessException do Spring
//    (SQLException vira DataAccessException — mais fácil de tratar)
// =============================================================================
@Repository

// =============================================================================
// JpaRepository<T, ID> — A interface mais importante do Spring Data
// =============================================================================
//
// T  = tipo da Entity que este repositório gerencia → User
// ID = tipo da chave primária da Entity            → Long
//
// Ao herdar JpaRepository, ganhamos AUTOMATICAMENTE (sem escrever nada):
//
//   CRUD básico:
//   save(entity)           → INSERT ou UPDATE (decide pelo id: null = INSERT)
//   findById(id)           → SELECT WHERE id = ? → retorna Optional<User>
//   findAll()              → SELECT * FROM users
//   deleteById(id)         → DELETE WHERE id = ?
//   delete(entity)         → DELETE WHERE id = entity.id
//   existsById(id)         → SELECT COUNT(*) WHERE id = ? > 0
//   count()                → SELECT COUNT(*) FROM users
//
//   Em lote:
//   saveAll(list)          → INSERT/UPDATE múltiplos registros
//   findAllById(ids)       → SELECT WHERE id IN (?, ?, ?)
//   deleteAllById(ids)     → DELETE WHERE id IN (?, ?, ?)
//
//   Paginação:
//   findAll(Pageable)      → SELECT com LIMIT e OFFSET
//   findAll(Sort)          → SELECT com ORDER BY
//
// HIERARQUIA da interface (do mais genérico ao mais específico):
//   Repository
//     └── CrudRepository       (save, findById, findAll, delete, count)
//           └── PagingAndSortingRepository  (findAll com Sort e Pageable)
//                 └── JpaRepository        (flush, saveAndFlush, deleteInBatch)
//
// USE JpaRepository como padrão — é o mais completo para Spring + JPA.
// =============================================================================
public interface UserRepository extends JpaRepository<User, Long> {

    // =========================================================================
    // QUERY METHODS — Spring Data gera o SQL pelo nome do método
    // =========================================================================
    //
    // Esta é a funcionalidade mais "mágica" do Spring Data.
    // Você escreve a assinatura do método seguindo uma convenção de nomes,
    // e o Spring Data GERA o SQL automaticamente em tempo de inicialização.
    //
    // Anatomia do nome:
    //   find   By   Email
    //   ────   ──   ─────
    //   verbo  sep  campo da Entity (exato, case-sensitive)
    //
    // Verbos disponíveis: find, get, read, count, exists, delete
    //
    // Conectores: And, Or, Between, LessThan, GreaterThan, Like,
    //             Containing, StartingWith, EndingWith, In, NotIn,
    //             IsNull, IsNotNull, OrderBy, Not, True, False
    //
    // O Spring verifica em tempo de boot se os campos existem na Entity.
    // Se você escrever findByEmaail (typo), o app NÃO vai subir — falha rápido.
    // =========================================================================

    // SELECT * FROM users WHERE email = ? LIMIT 1
    // Optional<User> → força o chamador a tratar o caso "não encontrado"
    // em vez de receber null e possivelmente um NullPointerException
    Optional<User> findByEmail(String email);

    // SELECT * FROM users WHERE email = ?
    // Usado no Spring Security para verificar se o email já existe
    boolean existsByEmail(String email);

    // SELECT * FROM users WHERE role = ?
    // User.Role é o enum — o JPA converte automaticamente para a string "DRIVER"
    List<User> findByRole(User.Role role);

    // SELECT * FROM users WHERE role = ? AND active = ?
    // Dois campos: And conecta as condições com AND no SQL
    List<User> findByRoleAndActive(User.Role role, Boolean active);

    // SELECT * FROM users WHERE subscription_status = ?
    List<User> findBySubscriptionStatus(User.SubscriptionStatus status);

    // =========================================================================
    // @Query — JPQL quando o nome do método ficaria longo demais
    // =========================================================================
    //
    // JPQL (Jakarta Persistence Query Language) é como SQL, mas opera sobre
    // ENTITIES e seus CAMPOS JAVA, não sobre tabelas e colunas do banco.
    //
    // Diferenças importantes JPQL vs SQL:
    //   SQL:   SELECT * FROM users WHERE is_active = 1
    //   JPQL:  SELECT u FROM User u WHERE u.active = true
    //          ─────────────────────────────────────────
    //          "User" = nome da classe Java (não da tabela)
    //          "u.active" = nome do campo Java (não da coluna)
    //
    // Vantagem: independente do banco de dados.
    // O Hibernate traduz o JPQL para o SQL dialeto correto (MySQL, PostgreSQL etc.)
    //
    // :email → parâmetro nomeado, ligado com @Param("email")
    // ?1     → parâmetro posicional (evite — menos legível)
    // =========================================================================

    // Busca usuarios drivers ativos com paginação — usada no painel admin
    // "u.role = com.motoapp.entity.User.Role.DRIVER" ← caminho completo do enum
    // ou importamos o enum no início do método com o nome simplificado
    @Query("SELECT u FROM User u WHERE u.role = 'DRIVER' AND u.active = true " +
            "ORDER BY u.createdAt DESC")
    List<User> findActiveDrivers();

    // Busca por nome OU email — útil para campo de busca no admin
    // LOWER() → case-insensitive: "João" encontra "joao", "JOAO", "João"
    // CONCAT('%', :term, '%') → equivale ao LIKE '%termo%'
    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<User> searchByNameOrEmail(@Param("term") String term);

    // =========================================================================
    // @Modifying + @Transactional — Queries de escrita com @Query
    // =========================================================================
    //
    // Por padrão, @Query só faz SELECT (leitura).
    // Para UPDATE ou DELETE com @Query, precisamos de:
    //
    // @Modifying   → avisa o Spring Data que esta query modifica dados
    //                sem isso, o Spring lança InvalidDataAccessApiUsageException
    //
    // @Transactional → garante que a operação ocorre dentro de uma transação.
    //                  Sem transação, o Hibernate não executa queries de escrita.
    //
    //                  Normalmente a transação vem do @Service (que anotamos
    //                  com @Transactional). Aqui colocamos também como segurança
    //                  para chamadas diretas ao repository em testes.
    //
    // clearAutomatically = true → limpa o cache de primeiro nível do Hibernate
    //                             após a query. Necessário quando a query UPDATE
    //                             não passa pelo ciclo de vida normal do JPA.
    //                             Sem isso, entidades em memória ficam desatualizadas.
    // =========================================================================

    // UPDATE users SET active = false WHERE id = ?
    // Soft delete: não apaga do banco, apenas desativa
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE User u SET u.active = false WHERE u.id = :id")
    void softDeleteById(@Param("id") Long id);

    // UPDATE users SET first_access = false, password = ? WHERE id = ?
    // Usado quando o motorista troca a senha no primeiro acesso
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE User u SET u.password = :password, u.firstAccess = false WHERE u.id = :id")
    void updatePasswordAndClearFirstAccess(@Param("id") Long id,
                                           @Param("password") String password);

    // Conta motoristas ativos — usado no dashboard do admin (KPI)
    @Query("SELECT COUNT(u) FROM User u WHERE u.role = 'DRIVER' AND u.active = true")
    long countActiveDrivers();
}