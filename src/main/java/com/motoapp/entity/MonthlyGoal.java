package com.motoapp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(
    name = "monthly_goals",
    uniqueConstraints = {
        // Um motorista tem no máximo UMA meta por mês/ano
        @UniqueConstraint(
            name = "uk_monthly_goals_user_year_month",
            columnNames = {"user_id", "year", "month"}
        )
    }
)
public class MonthlyGoal extends BaseEntity {

    @Override
    @EqualsAndHashCode.Include
    public Long getId() {
        return super.getId();
    }

    // ── Relacionamento ────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "Usuário é obrigatório")
    private User user;

    // ── Período da meta ───────────────────────────────────────────────────────

    /*
     * Por que year e month separados em vez de LocalDate?
     *
     * Uma meta é para um MÊS INTEIRO, não uma data específica.
     * Guardar como year=2024, month=5 é mais semântico e direto
     * do que LocalDate(2024, 5, 1) — que implicaria um dia específico.
     *
     * Também facilita queries:
     *   WHERE year = 2024 AND month = 5
     * em vez de:
     *   WHERE goal_date >= '2024-05-01' AND goal_date < '2024-06-01'
     */
    @Column(nullable = false)
    @NotNull(message = "Ano é obrigatório")
    @Min(value = 2020, message = "Ano inválido")
    private Integer year;

    @Column(nullable = false)
    @NotNull(message = "Mês é obrigatório")
    @Min(value = 1, message = "Mês deve ser entre 1 e 12")
    @Max(value = 12, message = "Mês deve ser entre 1 e 12")
    private Integer month;

    // ── Valor da meta ─────────────────────────────────────────────────────────

    // Meta de faturamento bruto que o motorista quer atingir no mês
    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull(message = "Valor da meta é obrigatório")
    @DecimalMin(value = "0.00", message = "Meta não pode ser negativa")
    private BigDecimal goalAmount;

    // =========================================================================
    // MÉTODO DE DOMÍNIO — Percentual de progresso em relação à meta
    // =========================================================================
    //
    // Recebe o valor atual ganho no mês e retorna o percentual atingido.
    //
    // BigDecimal.compareTo(BigDecimal.ZERO) == 0 → é a forma correta de
    // verificar se um BigDecimal é zero. NÃO use equals(BigDecimal.ZERO):
    //   new BigDecimal("0").equals(new BigDecimal("0.00")) → FALSE (!)
    //   new BigDecimal("0").compareTo(new BigDecimal("0.00")) → 0 (correto)
    //
    // Por que? equals() compara valor E escala. compareTo() compara só o valor.
    // =========================================================================
    public double calculateProgress(BigDecimal currentEarnings) {
        if (goalAmount == null || goalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        // (currentEarnings / goalAmount) * 100
        return currentEarnings
                .divide(goalAmount, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .doubleValue();
    }

    // Retorna true se a meta foi atingida ou superada
    public boolean isAchieved(BigDecimal currentEarnings) {
        return currentEarnings.compareTo(goalAmount) >= 0;
    }
}
