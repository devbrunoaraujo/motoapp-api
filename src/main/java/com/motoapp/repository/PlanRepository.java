package com.motoapp.repository;

import com.motoapp.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    // =========================================================================
    // QUERY METHODS
    // =========================================================================

    // SELECT * FROM plans WHERE active = true ORDER BY price ASC
    //
    // Perceba a convenção de nome composto:
    //   find    → verbo (retorna resultados)
    //   By      → separador obrigatório
    //   Active  → campo da Entity (active)
    //   OrderBy → palavra-chave de ordenação
    //   Price   → campo para ordenar (price)
    //   Asc     → direção (Asc ou Desc)
    //
    // O Spring Data lê esse nome e monta o SQL:
    //   SELECT * FROM plans WHERE active = ? ORDER BY price ASC
    //
    // Usada no frontend para listar os planos disponíveis para novos clientes.
    List<Plan> findByActiveTrueOrderByPriceAsc();

    // SELECT * FROM plans WHERE name = ? (case-insensitive)
    //
    // IgnoreCase → adiciona LOWER() em ambos os lados no SQL:
    //   WHERE LOWER(name) = LOWER(?)
    //
    // Útil para evitar duplicatas: "basico", "Basico", "BASICO"
    // seriam encontrados como o mesmo plano.
    Optional<Plan> findByNameIgnoreCase(String name);

    // =========================================================================
    // @Query — JPQL para queries com lógica mais elaborada
    // =========================================================================

    // Busca todos os planos ativos com a contagem de assinantes ativos.
    //
    // Esta é uma query mais avançada — vamos destrinchá-la:
    //
    // SELECT p, COUNT(s)
    //   → seleciona o plano E a contagem de assinaturas
    //
    // FROM Plan p
    //   → itera sobre a Entity Plan (alias "p")
    //
    // LEFT JOIN p.subscriptions s
    //   → LEFT JOIN porque queremos planos SEM assinantes também
    //     (INNER JOIN excluiria planos sem nenhuma assinatura)
    //   → p.subscriptions → campo Java da Entity, não coluna do banco
    //
    // ON s.status = 'ACTIVE'
    //   → filtra as assinaturas contadas (só as ativas)
    //   → Em JPQL, a condição do JOIN vai no WHERE, não no ON
    //     Veremos isso na versão correta abaixo com WITH
    //
    // GROUP BY p
    //   → agrupa por plano para o COUNT funcionar
    //
    // ORDER BY p.price ASC
    //   → ordena do mais barato para o mais caro
    //
    // Retorna Object[] porque temos dois valores (Plan + Long).
    // No Service vamos desempacotar: result[0] = Plan, result[1] = Long
    //
    // NOTA PARA O APRENDIZADO:
    // Em JPQL, não existe ON em JOINs — as condições adicionais
    // do JOIN vão sempre no WHERE. Esta é uma diferença importante do SQL.
    @Query("SELECT p, COUNT(s) FROM Plan p " +
            "LEFT JOIN p.subscriptions s " +
            "WHERE p.active = true " +
            "AND (s IS NULL OR s.status = com.motoapp.entity.Subscription.Status.ACTIVE) " +
            "GROUP BY p " +
            "ORDER BY p.price ASC")
    List<Object[]> findActivePlansWithSubscriberCount();

    // Verifica se já existe um plano com este nome (para evitar duplicatas no admin)
    boolean existsByNameIgnoreCase(String name);
}