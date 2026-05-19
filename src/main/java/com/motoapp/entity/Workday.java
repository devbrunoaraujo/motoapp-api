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
@Table(
    name = "workdays",
    uniqueConstraints = {
        // Um motorista não pode ter dois registros no mesmo dia
        // O banco rejeita antes mesmo da aplicação poder verificar
        @UniqueConstraint(
            name = "uk_workdays_user_date",
            columnNames = {"user_id", "work_date"}
        )
    }
)
public class Workday extends BaseEntity {

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

    // ── Dados do dia ──────────────────────────────────────────────────────────

    /*
     * LocalDate → apenas a data do dia trabalhado (sem hora).
     * Um motorista trabalha em um DIA — não importa a hora exata.
     * Usar LocalDate evita problemas de timezone em comparações de data.
     */
    @Column(name = "work_date", nullable = false)
    @NotNull(message = "Data é obrigatória")
    private LocalDate workDate;

    // Quilômetros rodados no dia
    // precision=8, scale=2 → máximo 999999.99 km (mais que suficiente)
    @Column(nullable = false, precision = 8, scale = 2)
    @NotNull(message = "KM rodados é obrigatório")
    @DecimalMin(value = "0.01", message = "KM deve ser maior que zero")
    private BigDecimal kmDriven;

    // Valor bruto ganho no app (antes de descontar combustível e despesas)
    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull(message = "Ganho bruto é obrigatório")
    @DecimalMin(value = "0.00", message = "Ganho não pode ser negativo")
    private BigDecimal grossEarnings;

    // ── Campos PRO ────────────────────────────────────────────────────────────

    // Plataforma utilizada no dia (funcionalidade PRO)
    // Ex: "uber", "99", "indrive", "ifood"
    // nullable = true → campo opcional (plano básico não preenche)
    @Column(length = 30)
    private String platform;

    // Horas trabalhadas no dia (funcionalidade PRO para calcular R$/hora)
    // precision=4, scale=2 → máximo 99.99 horas (suficiente para um dia)
    @Column(precision = 4, scale = 2)
    private BigDecimal hoursWorked;

    // Observações livres do motorista
    @Column(length = 500)
    private String notes;

    // =========================================================================
    // MÉTODOS DE DOMÍNIO
    // =========================================================================

    /*
     * Calcula o custo de combustível deste dia usando os dados do veículo.
     *
     * Recebe o Vehicle como parâmetro (não é campo da entity) porque:
     * 1. Evita criar um relacionamento @ManyToOne Vehicle aqui
     *    (o veículo ativo pode mudar — não queremos acoplar)
     * 2. O Service passa o veículo correto na hora do cálculo
     * 3. A lógica de qual veículo usar fica no Service (regra de negócio)
     */
    public BigDecimal calculateFuelCost(Vehicle vehicle) {
        if (vehicle == null) return BigDecimal.ZERO;
        return vehicle.calculateFuelCostForKm(this.kmDriven);
    }

    /*
     * Saldo do dia = ganho bruto - custo de combustível.
     * As despesas mensais (fixas e eventuais) são deduzidas no Service,
     * pois não pertencem a um dia específico.
     */
    public BigDecimal calculateDailyBalance(Vehicle vehicle) {
        return this.grossEarnings.subtract(calculateFuelCost(vehicle));
    }

    /*
     * Ganho por hora (só disponível quando hoursWorked foi preenchido).
     * Retorna null se horas não foram informadas — o Service trata isso.
     */
    public BigDecimal calculateEarningsPerHour() {
        if (hoursWorked == null || hoursWorked.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return grossEarnings.divide(hoursWorked, 2, java.math.RoundingMode.HALF_UP);
    }
}
