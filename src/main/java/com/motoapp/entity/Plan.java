package com.motoapp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "subscriptions")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "plans")
// =============================================================================
// extends BaseEntity → herda id, createdAt, updatedAt, @PrePersist, @PreUpdate
// Removemos daqui todos esses campos que estavam duplicados.
// =============================================================================
public class Plan extends BaseEntity {

    @Override
    @EqualsAndHashCode.Include
    public Long getId() {
        return super.getId();
    }

    // ── Dados do plano ────────────────────────────────────────────────────────

    @Column(nullable = false, length = 100)
    @NotBlank(message = "Nome do plano é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /*
     * NUNCA use double/float para dinheiro em Java.
     * double: 19.90 * 3 = 59.699999999999996 (imprecisão binária de ponto flutuante)
     * BigDecimal: new BigDecimal("19.90").multiply(new BigDecimal("3")) = 59.70
     *
     * precision=10, scale=2 → DECIMAL(10,2) no MySQL: máximo 99999999.99
     */
    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull(message = "Preço é obrigatório")
    @DecimalMin(value = "0.00", message = "Preço não pode ser negativo")
    private BigDecimal price;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum BillingCycle {
        MONTHLY,  // cobrança mensal
        YEARLY    // cobrança anual (geralmente com desconto)
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    /*
     * Features do plano como JSON no banco via AttributeConverter.
     * Exemplo armazenado: ["Dashboard financeiro","Registro diário","Relatórios mensais"]
     *
     * @Convert → instrui o JPA a usar StringListConverter para
     *            serializar (Java→banco) e deserializar (banco→Java).
     */
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> features;

    @Column(nullable = false)
    @Builder.Default
    @Min(value = 0, message = "Dias de trial não pode ser negativo")
    private Integer trialDays = 7;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // ── Relacionamentos ───────────────────────────────────────────────────────

    /*
     * mappedBy = "plan" → Subscription é o lado DONO (tem a FK plan_id no banco).
     *                      Plan é o lado INVERSO — não gerencia a coluna.
     * fetch = LAZY      → não carrega subscriptions ao buscar um plano.
     * cascade = {}      → deletar um plano NÃO deleta assinaturas (preserva histórico).
     */
    @OneToMany(mappedBy = "plan", fetch = FetchType.LAZY)
    private List<Subscription> subscriptions;

    // =========================================================================
    // CONVERTER — List<String> ↔ JSON TEXT no banco
    // =========================================================================
    //
    // AttributeConverter<X, Y>:
    //   X = tipo Java → List<String>
    //   Y = tipo banco → String (TEXT com JSON)
    //
    // Classe estática interna: só usada aqui em Plan.
    // Se outra entity precisasse, extrairíamos para com.motoapp.config.
    // =========================================================================
    @Converter
    public static class StringListConverter
            implements AttributeConverter<List<String>, String> {

        // Java → Banco: serializa List<String> para JSON string
        @Override
        public String convertToDatabaseColumn(List<String> list) {
            if (list == null || list.isEmpty()) return "[]";

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append("\"")
                  .append(list.get(i).replace("\"", "\\\""))
                  .append("\"");
                if (i < list.size() - 1) sb.append(",");
            }
            return sb.append("]").toString();
        }

        // Banco → Java: deserializa JSON string para List<String>
        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank() || dbData.equals("[]")) {
                return List.of();
            }
            // Remove colchetes e aspas, divide por vírgula
            String cleaned = dbData.trim()
                    .replaceAll("^\\[|\\]$", "")
                    .replaceAll("\"", "");

            return cleaned.isBlank()
                    ? List.of()
                    : List.of(cleaned.split(",\\s*"));
        }
    }
}
