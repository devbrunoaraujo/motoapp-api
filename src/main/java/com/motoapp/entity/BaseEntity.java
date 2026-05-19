package com.motoapp.entity;

// =============================================================================
// IMPORTS
// =============================================================================
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// =============================================================================
// @MappedSuperclass — O coração desta classe
// =============================================================================
//
// Esta anotação diz ao JPA:
//   "Os campos desta classe devem ser mapeados para as tabelas das
//    subclasses, mas NÃO crie uma tabela própria para esta classe."
//
// Diferença entre as três abordagens de herança no JPA:
//
// 1. @MappedSuperclass (o que usamos)
//    ✔ Campos da superclasse vão para a tabela de CADA subclasse
//    ✔ Sem tabela própria para a superclasse
//    ✔ Sem JOIN entre tabelas — performance máxima
//    ✔ Ideal para campos técnicos compartilhados (id, auditoria)
//
// 2. @Inheritance(SINGLE_TABLE)
//    ✔ Uma única tabela para toda a hierarquia
//    ✗ Muitas colunas nullable (colunas de subclasses ficam null)
//    ✗ Difícil de escalar com muitas subclasses
//
// 3. @Inheritance(JOINED)
//    ✔ Uma tabela por classe, unidas por FK
//    ✗ Sempre precisa de JOIN para buscar dados completos
//    ✗ Mais lento para leituras
//
// Para campos de infraestrutura (id, timestamps) → @MappedSuperclass
// Para hierarquias de domínio (Animal→Cachorro→Poodle) → SINGLE_TABLE ou JOINED
// =============================================================================
@MappedSuperclass

// =============================================================================
// LOMBOK
// =============================================================================
//
// @Getter e @Setter na superclasse: as subclasses herdam os métodos gerados.
// Plan, User, Subscription etc. vão ter getId(), getCreatedAt() etc.
// automaticamente, sem precisar declarar nada.
//
// Não usamos @Data aqui porque:
// 1. @Data inclui @EqualsAndHashCode, que em entities JPA deve ser baseado
//    apenas no id — e cada entity precisa controlar isso individualmente.
// 2. @Data inclui @ToString, que pode causar lazy loading indesejado
//    ao tentar imprimir relacionamentos.
// =============================================================================
@Getter
@Setter
public abstract class BaseEntity {

    // =========================================================================
    // CHAVE PRIMÁRIA — Presente em TODAS as entities
    // =========================================================================
    //
    // Por que Long e não Integer?
    // Integer suporta até ~2 bilhões. Long suporta até ~9 quintilhões.
    // Em sistemas SaaS com muitos registros, Long é a escolha segura.
    // O custo de memória é mínimo (8 bytes vs 4 bytes por registro).
    //
    // Por que não UUID?
    // UUID (ex: "550e8400-e29b-41d4-a716-446655440000") é melhor para:
    // - Sistemas distribuídos (múltiplos servidores gerando IDs)
    // - Quando você não quer expor sequência numérica na URL
    // - Quando precisa gerar o ID antes de salvar no banco
    //
    // Long com AUTO_INCREMENT é melhor para:
    // - Sistemas menores e médios (nosso caso)
    // - Performance de índice (números são mais rápidos que strings no índice)
    // - Joins (comparar Long é mais rápido que comparar UUID)
    //
    // GenerationType.IDENTITY = usa o AUTO_INCREMENT do MySQL.
    // Alternativas:
    //   SEQUENCE → usa sequences do banco (PostgreSQL, Oracle)
    //   TABLE    → usa uma tabela auxiliar para gerar IDs (mais lento, evitar)
    //   AUTO     → o JPA escolhe a estratégia (evitar — comportamento imprevisível)
    // =========================================================================
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================================================
    // AUDITORIA — Quando o registro foi criado e modificado
    // =========================================================================
    //
    // LocalDateTime vs Instant vs ZonedDateTime — qual usar?
    //
    // LocalDateTime (nosso caso):
    //   - Data e hora SEM informação de timezone
    //   - Simples, funciona bem quando aplicação e banco estão no mesmo timezone
    //   - Configuramos America/Sao_Paulo no application.yml → consistente
    //
    // Instant:
    //   - Momento exato no tempo (UTC, sem timezone)
    //   - Ideal para sistemas internacionais com múltiplos timezones
    //   - Converta para o timezone do usuário na camada de apresentação
    //
    // ZonedDateTime:
    //   - Data + hora + timezone explícito
    //   - Mais verboso, nem sempre suportado bem por todos os bancos
    //
    // Para um SaaS brasileiro com usuários no mesmo timezone → LocalDateTime
    // Para um SaaS global → Instant
    //
    // updatable = false → após o INSERT, o JPA NUNCA vai incluir createdAt
    //                     em um UPDATE SQL. O banco mantém o valor original.
    //                     Sem isso, um save() acidental poderia sobrescrever
    //                     a data de criação.
    // =========================================================================
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // =========================================================================
    // LIFECYCLE CALLBACKS — Definidos UMA VEZ, herdados por TODAS as entities
    // =========================================================================
    //
    // O JPA respeita os callbacks definidos em @MappedSuperclass.
    // Ou seja: quando salvarmos um Plan, User, Subscription etc.,
    // o JPA vai chamar ESTES métodos automaticamente — sem nenhuma
    // configuração extra nas subclasses.
    //
    // protected → subclasses podem sobrescrever se precisarem de
    //              comportamento extra. Exemplo:
    //
    //   @Entity
    //   public class Subscription extends BaseEntity {
    //       @PrePersist  // sobrescreve e chama o super
    //       protected void onCreate() {
    //           super.onCreate();  // roda createdAt e updatedAt
    //           this.status = Status.TRIAL;  // comportamento extra
    //       }
    //   }
    // =========================================================================

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        // Inicializa ambos os campos no primeiro save
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        // Atualiza apenas updatedAt — createdAt é imutável (updatable = false)
        this.updatedAt = LocalDateTime.now();
    }
}
