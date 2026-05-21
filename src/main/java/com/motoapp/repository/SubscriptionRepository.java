package com.motoapp.repository;

import com.motoapp.entity.Subscription;
import com.motoapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // =========================================================================
    // BUSCA DA ASSINATURA ATIVA
    // =========================================================================

    // Busca a assinatura mais recente de um usuário.
    //
    // Por que Optional e não List?
    // Para a tela do motorista, precisamos de UMA assinatura — a atual.
    // Optional comunica: "pode existir ou não, trate os dois casos".
    //
    // ORDER BY createdAt DESC → a mais recente primeiro
    // LIMIT 1                 → em JPQL usamos o método da query, não LIMIT
    //
    // Mas JPQL não tem LIMIT diretamente...
    // A solução é usar o parâmetro Pageable ou a anotação abaixo.
    // Aqui usamos findFirst que o Spring Data traduz para LIMIT 1:
    Optional<Subscription> findFirstByUserOrderByCreatedAtDesc(User user);

    // Versão com o ID do usuário — evita carregar a Entity User só para buscar
    // a assinatura. Mais eficiente quando já temos o ID em mãos.
    @Query("SELECT s FROM Subscription s WHERE s.user.id = :userId " +
            "ORDER BY s.createdAt DESC")
    Optional<Subscription> findLatestByUserId(@Param("userId") Long userId);

    // Busca assinatura ativa OU em trial de um usuário.
    // Usada no SubscriptionMiddleware para verificar acesso.
    @Query("SELECT s FROM Subscription s WHERE s.user.id = :userId " +
            "AND s.status IN ('TRIAL', 'ACTIVE') " +
            "ORDER BY s.createdAt DESC")
    Optional<Subscription> findActiveOrTrialByUserId(@Param("userId") Long userId);

    // =========================================================================
    // QUERIES DE EXPIRAÇÃO AUTOMÁTICA
    // =========================================================================
    //
    // Estas queries são executadas pelo job automático que roda diariamente
    // para bloquear usuários com trial ou assinatura expirados.
    //
    // LocalDate.now() é passado como parâmetro (não gerado dentro da query)
    // por uma razão importante: TESTABILIDADE.
    // Se a data fosse gerada dentro da query, não conseguiríamos testar
    // com datas diferentes sem alterar o relógio do sistema.
    // Passando como parâmetro, o teste pode chamar com qualquer data:
    //   repository.findExpiredTrials(LocalDate.of(2024, 12, 31))
    // =========================================================================

    // Trials que já passaram da data de expiração e ainda estão como TRIAL
    // (ainda não foram bloqueados pelo job)
    @Query("SELECT s FROM Subscription s WHERE s.status = 'TRIAL' " +
            "AND s.trialEndsAt < :today")
    List<Subscription> findExpiredTrials(@Param("today") LocalDate today);

    // Assinaturas pagas que venceram e ainda estão como ACTIVE
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' " +
            "AND s.expiresAt < :today")
    List<Subscription> findExpiredPaidSubscriptions(@Param("today") LocalDate today);

    // Trials que vencem nos próximos N dias — usada para alertas no admin
    // BETWEEN :today AND :limitDate → hoje até a data limite
    @Query("SELECT s FROM Subscription s WHERE s.status = 'TRIAL' " +
            "AND s.trialEndsAt BETWEEN :today AND :limitDate " +
            "ORDER BY s.trialEndsAt ASC")
    List<Subscription> findTrialsExpiringSoon(
            @Param("today") LocalDate today,
            @Param("limitDate") LocalDate limitDate);

    // =========================================================================
    // @Modifying — Atualização em lote (bulk update)
    // =========================================================================
    //
    // Em vez de:
    //   1. Carregar cada Subscription em memória
    //   2. Alterar o status
    //   3. Salvar uma por uma
    //   → N queries de UPDATE separadas
    //
    // Fazemos um único UPDATE em lote:
    //   UPDATE subscriptions SET status = 'BLOCKED' WHERE ...
    //   → 1 query, independente de quantos registros afeta
    //
    // Esta é uma diferença crítica de performance em produção.
    // Para 1000 usuários com trial expirado:
    //   Abordagem anterior: 1000 SELECTs + 1000 UPDATEs = 2000 queries
    //   Bulk update: 1 UPDATE = 1 query
    // =========================================================================

    // Bloqueia todos os trials expirados de uma vez
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Subscription s SET s.status = 'BLOCKED', " +
            "s.blockedAt = :now, " +
            "s.blockedReason = 'Trial de 7 dias encerrado' " +
            "WHERE s.status = 'TRIAL' AND s.trialEndsAt < :today")
    int blockExpiredTrials(
            @Param("today") LocalDate today,
            @Param("now") java.time.LocalDateTime now);

    // Bloqueia todas as assinaturas pagas vencidas de uma vez
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Subscription s SET s.status = 'BLOCKED', " +
            "s.blockedAt = :now, " +
            "s.blockedReason = 'Assinatura vencida — aguardando pagamento' " +
            "WHERE s.status = 'ACTIVE' AND s.expiresAt < :today")
    int blockExpiredPaidSubscriptions(
            @Param("today") LocalDate today,
            @Param("now") java.time.LocalDateTime now);

    // =========================================================================
    // MÉTRICAS DO DASHBOARD ADMIN
    // =========================================================================

    // Contagem por status — usada nos cards de KPI do admin
    // Retorna pares [status, quantidade]:
    //   [["TRIAL", 42], ["ACTIVE", 18], ["BLOCKED", 5]]
    @Query("SELECT s.status, COUNT(s) FROM Subscription s GROUP BY s.status")
    List<Object[]> countByStatus();

    // MRR — Monthly Recurring Revenue (Receita Mensal Recorrente)
    //
    // O MRR é a métrica financeira mais importante de um SaaS.
    // Calculamos somando o preço dos planos de todas as assinaturas ATIVAS.
    //
    // COALESCE(SUM(...), 0) → retorna 0 se não houver assinaturas ativas
    //                         (sem COALESCE, retornaria null — causaria NPE)
    //
    // Por que p.price e não s.amountPaid?
    // amountPaid é o que foi pago na última renovação (pode ter desconto).
    // p.price é o valor recorrente do plano — o que vai entrar todo mês.
    // MRR usa o valor recorrente, não o valor já recebido.
    @Query("SELECT COALESCE(SUM(s.plan.price), 0) FROM Subscription s " +
            "WHERE s.status = 'ACTIVE'")
    BigDecimal calculateMRR();

    // Histórico de assinaturas de um usuário — para a tela "Minha Assinatura"
    @Query("SELECT s FROM Subscription s WHERE s.user.id = :userId " +
            "ORDER BY s.createdAt DESC")
    List<Subscription> findAllByUserId(@Param("userId") Long userId);

    // Lista todas as assinaturas com dados do usuário e plano — painel admin
    // JOIN FETCH → carrega User e Plan junto com a Subscription em 1 query
    //              Evita o problema N+1: sem fetch, acessar s.getUser()
    //              dentro de um loop dispararia 1 SELECT por assinatura
    @Query("SELECT s FROM Subscription s " +
            "JOIN FETCH s.user " +
            "JOIN FETCH s.plan " +
            "ORDER BY s.updatedAt DESC")
    List<Subscription> findAllWithUserAndPlan();
}