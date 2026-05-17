package com.motoapp.entity;

// =============================================================================
// IMPORTS — Anotações JPA (Jakarta Persistence API)
// =============================================================================
//
// JPA é o padrão Java para mapeamento objeto-relacional (ORM).
// O Hibernate é a implementação do JPA que o Spring Boot usa por baixo.
//
// Regra prática: quando você vê "jakarta.persistence.*", são anotações
// do padrão JPA. Quando vê "org.hibernate.*", são extensões proprietárias.
// Prefira sempre as anotações JPA — são portáveis entre implementações.
// =============================================================================

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// =============================================================================
// ANOTAÇÕES LOMBOK — Eliminam código boilerplate
// =============================================================================
//
// @Getter         → gera getUser(), getEmail(), getPassword()... para todos os campos
// @Setter         → gera setName(), setEmail()... para todos os campos
// @NoArgsConstructor → gera: public User() {}  (exigido pelo JPA/Hibernate)
// @AllArgsConstructor → gera construtor com TODOS os campos como parâmetros
// @Builder        → habilita o padrão Builder:
//                   User user = User.builder().name("João").email("j@j.com").build();
//                   Muito mais legível que construtores com 10 parâmetros!
// @ToString       → gera toString() — exclua campos que causam loops (listas)
//                   exclude = evita StackOverflow em relacionamentos bidirecionais
// @EqualsAndHashCode → gera equals() e hashCode() baseados só no id
//                      Importante: sem isso, duas instâncias do mesmo usuário
//                      seriam consideradas objetos diferentes pelo Java
// =============================================================================
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"password", "subscriptions", "workdays", "expenses"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)

// =============================================================================
// ANOTAÇÕES JPA — Mapeamento objeto-relacional
// =============================================================================
//
// @Entity → diz ao JPA: "esta classe representa uma tabela no banco".
//           O Hibernate vai criar/gerenciar a tabela "users" automaticamente
//           conforme o ddl-auto configurado no application.yml.
//
// @Table  → especifica o nome da tabela e constraints a nível de banco.
//           uniqueConstraints garante unicidade diretamente no banco,
//           não só na aplicação — proteção dupla contra emails duplicados.
// =============================================================================
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                // Garante que dois usuários não podem ter o mesmo email
                // O banco vai rejeitar a inserção mesmo se a aplicação falhar
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        }
)

// =============================================================================
// IMPLEMENTS UserDetails — Integração com Spring Security
// =============================================================================
//
// UserDetails é uma interface do Spring Security.
// Ao implementá-la na nossa Entity, dizemos ao Spring:
// "use esta classe como o objeto de usuário autenticado".
//
// Vantagem: não precisamos de uma classe intermediária — a própria Entity
// já sabe como se comportar dentro do contexto de segurança.
// =============================================================================
public class User implements UserDetails {

    // =========================================================================
    // CAMPOS — Cada campo vira uma coluna na tabela "users"
    // =========================================================================

    // @Id         → esta coluna é a chave primária da tabela
    // @GeneratedValue → o banco gera o valor automaticamente
    // IDENTITY    → usa AUTO_INCREMENT do MySQL (1, 2, 3, 4...)
    //
    // DICA: Em sistemas distribuídos, prefira UUID como ID.
    // Para este projeto, Long é suficiente e mais simples.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include  // equals/hashCode baseado APENAS no id
    private Long id;

    // @Column → configura detalhes da coluna no banco
    // nullable = false → NOT NULL no banco de dados
    // length = 150     → VARCHAR(150) — sem isso, seria VARCHAR(255) padrão
    //
    // @NotBlank (Bean Validation) → valida a entrada antes de chegar no banco
    // É uma camada a mais de proteção: banco E aplicação validam juntos.
    @Column(nullable = false, length = 150)
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 150, message = "Nome deve ter entre 2 e 150 caracteres")
    private String name;

    @Column(nullable = false, unique = true, length = 200)
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    private String email;

    // A senha NUNCA é armazenada em texto puro — sempre com hash (BCrypt).
    // O campo não tem @NotBlank porque o BCrypt sempre gera uma string válida.
    // Também excluímos do @ToString para não vazar a senha nos logs.
    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 20)
    private String phone;

    // =========================================================================
    // ENUMS — Tipos com valores fixos
    // =========================================================================
    //
    // Role define o papel do usuário no sistema.
    // Usamos Enum em vez de String para segurança de tipos:
    // - String "ADMIN" pode virar "ADMlN" (L minúsculo) por engano
    // - Role.ADMIN nunca vai ter typo — o compilador garante
    //
    // @Enumerated(STRING) → salva "DRIVER" ou "ADMIN" no banco
    //                        (não o número ordinal — seria frágil)
    // ==========================================================================
    public enum Role {
        DRIVER,   // motorista comum
        ADMIN     // administrador do SaaS
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default  // garante o valor padrão ao usar o Builder
    private Role role = Role.DRIVER;

    // =========================================================================
    // STATUS DA ASSINATURA
    // =========================================================================
    public enum SubscriptionStatus {
        TRIAL,      // período de avaliação gratuita
        ACTIVE,     // assinatura paga e vigente
        BLOCKED,    // bloqueado por falta de pagamento
        NONE        // sem plano
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIAL;

    // Data em que o trial expira — null se não estiver em trial
    private LocalDate trialEndsAt;

    // Indica se o usuário precisa trocar a senha no próximo login
    // true = admin acabou de criar a conta, senha é temporária
    @Column(nullable = false)
    @Builder.Default
    private Boolean firstAccess = true;

    // Indica se a conta está ativa — false = soft delete
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // =========================================================================
    // AUDITORIA — Controle automático de quando registros foram criados/alterados
    // =========================================================================
    //
    // @Column(updatable = false) → o JPA nunca vai atualizar esta coluna
    //                              depois da inserção inicial
    //
    // Preenchemos esses campos no @PrePersist e @PreUpdate abaixo.
    // Alternativa mais poderosa: @EnableJpaAuditing + @CreatedDate
    // (veremos isso em outros entities mais simples — aqui fazemos manual
    // para você entender o que acontece por baixo)
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // =========================================================================
    // LIFECYCLE CALLBACKS — Executados automaticamente pelo JPA
    // =========================================================================
    //
    // @PrePersist → executado ANTES de INSERT no banco
    // @PreUpdate  → executado ANTES de UPDATE no banco
    //
    // São métodos "hook" — o JPA os chama automaticamente.
    // Perfeito para auditoria: o desenvolvedor não precisa lembrar
    // de setar createdAt manualmente em cada lugar que cria um User.
    // =========================================================================

    @PrePersist
    protected void onCreate() {
        // LocalDateTime.now() usa o timezone da JVM
        // Configuramos America/Sao_Paulo no application.yml
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // =========================================================================
    // IMPLEMENTAÇÃO DE UserDetails — Spring Security
    // =========================================================================
    //
    // A interface UserDetails obriga implementar estes métodos.
    // O Spring Security usa eles para:
    // - getAuthorities() → saber quais permissões o usuário tem
    // - getPassword()    → verificar a senha (o Lombok já gera este getter!)
    // - getUsername()    → identificar o usuário (usamos o email)
    // - isAccountNonExpired, isAccountNonLocked etc. → checagens de status
    //
    // IMPORTANTE: getPassword() e getUsername() já seriam gerados pelo Lombok,
    // mas o UserDetails precisa deles com assinaturas específicas — como o
    // Lombok os gera corretamente, o Spring os reconhece automaticamente.
    // =========================================================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Converte o enum Role em uma "authority" do Spring Security
        // "ROLE_" é o prefixo padrão esperado pelo Spring Security
        // Role.ADMIN  → "ROLE_ADMIN"
        // Role.DRIVER → "ROLE_DRIVER"
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }

    @Override
    public String getUsername() {
        // No Spring Security, "username" é o identificador único do usuário
        // No nosso sistema, usamos o email como identificador de login
        return this.email;
    }

    // Os métodos abaixo controlam o status da conta no Spring Security.
    // Todos retornam true por padrão — o controle de bloqueio faremos
    // manualmente via o campo "active" + SubscriptionStatus.
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Conta bloqueada = usuário não consegue autenticar
        // Ligamos ao nosso campo "active" para controle real
        return this.active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // Conta desativada = usuário não consegue usar o sistema
        return this.active;
    }
}