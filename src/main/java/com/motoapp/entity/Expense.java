package com.motoapp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "expenses")
public class Expense extends BaseEntity {

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

    // ── Categorias e tipos ────────────────────────────────────────────────────

    /*
     * Enum de categorias: valores fixos conhecidos em tempo de compilação.
     * Vantagem sobre String: o compilador garante que só valores válidos
     * são usados. Não existe Expense.Category.ALIMENTACAOO (typo) — o
     * compilador rejeita na hora.
     */
    public enum Category {
        MAINTENANCE,    // manutenção do veículo
        FOOD,           // alimentação
        CAR_PAYMENT,    // parcela do financiamento
        APP_FEE,        // taxa do aplicativo
        FUEL,           // combustível extra
        TOLL,           // pedágio
        INSURANCE,      // seguro
        OTHER           // outros
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @NotNull(message = "Categoria é obrigatória")
    private Category category;

    public enum ExpenseType {
        RECURRING,      // recorrente: todo mês (parcela, taxa app)
        OCCASIONAL      // eventual: aconteceu uma vez (conserto, alimentação)
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull(message = "Tipo é obrigatório")
    private ExpenseType type;

    // ── Dados da despesa ──────────────────────────────────────────────────────

    @Column(nullable = false, length = 255)
    @NotBlank(message = "Descrição é obrigatória")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal amount;

    // ── Campos condicionais por tipo ──────────────────────────────────────────

    /*
     * Para RECURRING (recorrente):
     *   recurrence → frequência: "monthly" ou "weekly"
     *   dueDay     → dia do mês em que vence (ex: 10 = todo dia 10)
     *
     * Para OCCASIONAL (eventual):
     *   expenseDate → data exata em que aconteceu
     *
     * Usamos @Column separados e nullable=true porque cada tipo
     * preenche campos diferentes. O Service valida a consistência:
     *   RECURRING  → recurrence e dueDay preenchidos
     *   OCCASIONAL → expenseDate preenchida
     */
    @Column(length = 20)
    private String recurrence;     // "monthly" | "weekly" | null

    private Integer dueDay;        // dia do vencimento (1-31) para recorrentes

    private LocalDate expenseDate; // data da despesa eventual

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true; // false = despesa desativada (sem deletar)
}
