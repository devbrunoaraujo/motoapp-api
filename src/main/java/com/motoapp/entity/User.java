package com.motoapp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"password", "subscriptions", "workdays", "expenses"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    }
)
// =============================================================================
// extends BaseEntity
// =============================================================================
// Herdamos: id, createdAt, updatedAt, @PrePersist, @PreUpdate
// Removemos daqui: tudo isso que estava duplicado antes.
//
// callSuper = false no @EqualsAndHashCode → o equals/hashCode usa apenas
// os campos marcados com @EqualsAndHashCode.Include DESTA classe (o id
// está na BaseEntity, então precisamos de @EqualsAndHashCode.Include lá
// também... mas como queremos controle granular, marcamos aqui via override.
//
// Solução: sobrescrever getId() com @EqualsAndHashCode.Include nesta classe.
// Isso é mais explícito e não depende de herança encadeada de anotações Lombok.
// =============================================================================
public class User extends BaseEntity implements UserDetails {

    // Sobrescrevemos getId() herdado para marcar com @Include
    // Lombok vai usar ESTE getId() para gerar equals/hashCode
    @Override
    @EqualsAndHashCode.Include
    public Long getId() {
        return super.getId();
    }

    // ── Dados pessoais ────────────────────────────────────────────────────────

    @Column(nullable = false, length = 150)
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 150, message = "Nome deve ter entre 2 e 150 caracteres")
    private String name;

    @Column(nullable = false, unique = true, length = 200)
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 20)
    private String phone;

    // ── Papel e status ────────────────────────────────────────────────────────

    public enum Role {
        DRIVER,
        ADMIN
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.DRIVER;

    public enum SubscriptionStatus {
        TRIAL,
        ACTIVE,
        BLOCKED,
        NONE
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIAL;

    private LocalDate trialEndsAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean firstAccess = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // ── Relacionamentos ───────────────────────────────────────────────────────

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Subscription> subscriptions;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Workday> workdays;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Expense> expenses;

    // ── Spring Security ───────────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return this.active; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return this.active; }
}
