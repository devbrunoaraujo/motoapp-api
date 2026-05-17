package com.motoapp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@ToString(exclude = "subscriptions")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100)
    @NotBlank(message = "Nome do plano é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /*
     * NUNCA use double/float para dinheiro em Java.
     * double: 19.90 * 3 = 59.699999999999996 (imprecisão binária)
     * BigDecimal: new BigDecimal("19.90").multiply(new BigDecimal("3")) = 59.70
     *
     * precision=10, scale=2 → DECIMAL(10,2) no MySQL (ex: 99999999.99)
     */
    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull(message = "Preço é obrigatório")
    @DecimalMin(value = "0.00", message = "Preço não pode ser negativo")
    private BigDecimal price;

    /*
     * Enum definido dentro da Entity porque só faz sentido aqui.
     * Se fosse usado em múltiplas entities, iria para arquivo separado.
     */
    public enum BillingCycle {
        MONTHLY,  // cobrança mensal
        YEARLY    // cobrança anual (geralmente com desconto)
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    /*
     * Features do plano armazenadas como JSON no banco.
     * @Convert usa o StringListConverter abaixo para serializar/deserializar.
     * Exemplo no banco: ["Dashboard","Registro diário","Relatórios mensais"]
     *
     * Alternativa mais robusta: tabela separada "plan_features".
     * Para exibição simples no frontend, JSON é pragmático e suficiente.
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

    /*
     * RELACIONAMENTO @OneToMany
     *
     * mappedBy = "plan" → o lado dono é Subscription (quem tem a FK no banco).
     *                      Plan é o lado inverso — não gerencia a coluna.
     *
     * fetch = LAZY → NÃO carrega assinaturas ao buscar um plano.
     *                Evita o problema SELECT N+1:
     *                  10 planos = 1 query
     *                  EAGER carregaria subscriptions de cada plano = 11 queries
     *                  LAZY + busca explícita quando necessário = melhor opção
     *
     * cascade = {} (nenhum) → deletar um plano NÃO deleta assinaturas.
     *                          Preservamos histórico financeiro.
     */
    @OneToMany(mappedBy = "plan", fetch = FetchType.LAZY)
    private List<Subscription> subscriptions;

    // ── Auditoria ─────────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // =========================================================================
    // CONVERTER — List<String> ↔ JSON TEXT no banco
    // =========================================================================
    //
    // AttributeConverter<X, Y>:
    //   X = tipo Java que queremos usar no código  → List<String>
    //   Y = tipo que vai para o banco              → String (JSON)
    //
    // Classe estática interna porque só é usada aqui em Plan.
    // Se outros entities precisassem, moveria para com.motoapp.config.
    // =========================================================================
    @Converter
    public static class StringListConverter
            implements AttributeConverter<List<String>, String> {

        // Java → Banco: List<String> vira JSON string
        // ["Dashboard financeiro","Registro diário","Relatórios mensais"]
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

        // Banco → Java: JSON string vira List<String>
        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank() || dbData.equals("[]")) {
                return List.of();
            }
            // Remove [ ] e aspas, divide pela vírgula
            String cleaned = dbData.trim()
                    .replaceAll("^\\[|\\]$", "")
                    .replaceAll("\"", "");

            return cleaned.isBlank()
                    ? List.of()
                    : List.of(cleaned.split(",\\s*"));
        }
    }
}