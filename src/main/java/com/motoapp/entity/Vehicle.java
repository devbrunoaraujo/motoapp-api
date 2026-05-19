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
@Table(name = "vehicles")
public class Vehicle extends BaseEntity {

    @Override
    @EqualsAndHashCode.Include
    public Long getId() {
        return super.getId();
    }

    // =========================================================================
    // RELACIONAMENTO — Cada veículo pertence a um único motorista
    // =========================================================================
    //
    // @OneToOne seria tecnicamente mais correto se cada usuário tiver
    // EXATAMENTE um veículo. Usamos @ManyToOne para permitir histórico:
    // o motorista pode trocar de carro e manter os registros anteriores.
    // O veículo "ativo" será o mais recente — definido na query do Service.
    // =========================================================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "Usuário é obrigatório")
    private User user;

    // ── Dados do veículo ──────────────────────────────────────────────────────

    @Column(length = 80)
    private String brand;       // Ex: Toyota, Honda, Volkswagen

    @Column(nullable = false, length = 100)
    @NotBlank(message = "Modelo é obrigatório")
    private String model;       // Ex: Corolla, HB20, Polo

    private Integer year;       // Ex: 2022

    @Column(length = 10)
    private String plate;       // Ex: ABC-1234

    // =========================================================================
    // CAMPOS DE CÁLCULO — Usados para calcular custo de combustível
    // =========================================================================
    //
    // fuelEfficiency: quantos km o carro faz por litro (km/L)
    //   Ex: 10.5 → o carro faz 10.5 km com 1 litro
    //   precision=5, scale=2 → máximo 999.99 km/L (mais que suficiente)
    //
    // fuelPrice: preço do combustível por litro em reais
    //   Ex: 6.89 → R$ 6,89 por litro
    //   precision=8, scale=3 → máximo 99999.999 (3 casas para centavos de litro)
    //
    // Custo por km = fuelPrice / fuelEfficiency
    //   Ex: 6.89 / 10.5 = R$ 0,656 por km
    // =========================================================================
    @Column(nullable = false, precision = 5, scale = 2)
    @NotNull(message = "Autonomia é obrigatória")
    @DecimalMin(value = "0.1", message = "Autonomia deve ser maior que zero")
    private BigDecimal fuelEfficiency;  // km/L

    @Column(nullable = false, precision = 8, scale = 3)
    @NotNull(message = "Preço do combustível é obrigatório")
    @DecimalMin(value = "0.001", message = "Preço deve ser maior que zero")
    private BigDecimal fuelPrice;       // R$ por litro

    // =========================================================================
    // MÉTODO DE DOMÍNIO — Cálculo encapsulado na Entity
    // =========================================================================
    //
    // O cálculo de custo de combustível é uma regra de negócio deste domínio.
    // Colocamos aqui para que o Service não precise repetir esta fórmula.
    //
    // BigDecimal.divide() exige escala e modo de arredondamento:
    //   scale = 4       → 4 casas decimais intermediárias (precisão)
    //   HALF_UP         → arredondamento padrão (0.5 → 1, não para baixo)
    //
    // Ao final, setScale(2) → retorna com 2 casas decimais para o cliente
    // =========================================================================
    public BigDecimal calculateFuelCostForKm(BigDecimal km) {
        if (fuelEfficiency == null || fuelEfficiency.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // Custo = (km / autonomia) * preço por litro
        return km.divide(fuelEfficiency, 4, java.math.RoundingMode.HALF_UP)
                 .multiply(fuelPrice)
                 .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
