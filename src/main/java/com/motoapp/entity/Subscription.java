package com.motoapp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "plan"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "subscriptions")
public class Subscription extends BaseEntity {

    @Override
    @EqualsAndHashCode.Include
    public Long getId() {
        return super.getId();
    }

    // =========================================================================
    // RELACIONAMENTOS @ManyToOne — O lado DONO (quem tem a FK no banco)
    // =========================================================================
    //
    // @ManyToOne → muitas assinaturas pertencem a UM usuário.
    //              Este lado é o DONO porque é aqui que a FK fica no banco:
    //              a coluna "user_id" existe na tabela "subscriptions".
    //
    // fetch = EAGER → carrega o User junto com a Subscription SEMPRE.
    //   Por que EAGER aqui e não LAZY?
    //   Em @ManyToOne, EAGER é seguro e geralmente o correto:
    //   - Você quase sempre precisa do User ao trabalhar com Subscription
    //   - É um único objeto (não uma lista) → sem risco de SELECT N+1
    //   - O JPA faz um único SELECT com JOIN, não duas queries separadas
    //
    //   REGRA PRÁTICA:
    //   @ManyToOne  → EAGER (padrão do JPA, um objeto, quase sempre necessário)
    //   @OneToMany  → LAZY  (padrão do JPA, coleção, carregue só se precisar)
    //
    // @JoinColumn → define o nome da coluna FK na tabela "subscriptions"
    //               sem isso, o JPA geraria um nome automático menos legível
    //               nullable = false → a FK não pode ser null (toda assinatura
    //               pertence a um usuário)
    // =========================================================================
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "Usuário é obrigatório")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    @NotNull(message = "Plano é obrigatório")
    private Plan plan;

    // ── Status da assinatura ──────────────────────────────────────────────────

    public enum Status {
        TRIAL,      // período gratuito de avaliação
        ACTIVE,     // pago e vigente
        BLOCKED,    // bloqueado por falta de pagamento
        CANCELLED   // cancelado pelo admin
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.TRIAL;

    // ── Datas de vigência ─────────────────────────────────────────────────────

    // LocalDate = apenas data, sem hora (dia de início/fim do plano)
    // LocalDateTime = data + hora (quando algo aconteceu exatamente)
    private LocalDate trialEndsAt;
    private LocalDate startsAt;
    private LocalDate expiresAt;

    // ── Informações do último pagamento ──────────────────────────────────────

    /*
     * Pagamento manual: o admin confirma e registra aqui.
     * amountPaid = valor efetivamente recebido (pode diferir do plano
     *              em caso de desconto, negociação etc.)
     */
    private LocalDateTime paidAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal amountPaid;

    // Observações do admin: "pago via Pix", "comprovante #123" etc.
    @Column(length = 500)
    private String notes;

    // ── Bloqueio ──────────────────────────────────────────────────────────────

    private LocalDateTime blockedAt;

    @Column(length = 255)
    private String blockedReason;

    // =========================================================================
    // MÉTODO DE CONVENIÊNCIA — Lógica de domínio na Entity
    // =========================================================================
    //
    // "Domain Logic na Entity" é um princípio do Domain-Driven Design (DDD).
    // A ideia: a Entity não é só um container de dados, ela também conhece
    // suas próprias regras.
    //
    // Em vez de escrever no Service:
    //   if (subscription.getStatus() == Status.TRIAL &&
    //       subscription.getTrialEndsAt() != null &&
    //       subscription.getTrialEndsAt().isBefore(LocalDate.now())) { ... }
    //
    // Escrevemos UMA vez aqui e chamamos de qualquer lugar:
    //   if (subscription.isExpired()) { ... }
    //
    // Regra de sênior: se a lógica só usa dados desta entity → coloque aqui.
    // Se precisar de outros serviços ou repositories → coloque no Service.
    // =========================================================================
    public boolean isTrialExpired() {
        return status == Status.TRIAL
                && trialEndsAt != null
                && trialEndsAt.isBefore(LocalDate.now());
    }

    public boolean isActive() {
        return status == Status.ACTIVE
                && expiresAt != null
                && !expiresAt.isBefore(LocalDate.now());
    }

    public boolean isBlocked() {
        return status == Status.BLOCKED;
    }
}
