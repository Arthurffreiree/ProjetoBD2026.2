-- ================================================================
-- Sistema de Gerenciamento de Hotel - Consultas da Etapa 03
-- Integrantes: Arthur Freire e Luís Melo
--
-- As 9 consultas abaixo são as mesmas executadas pela aplicação Java
-- (classe ConsultaDAO), com SQL explícito via JDBC.
-- Todas usam JOIN e subconsulta:
--   01 Ocupação atual por tipo de quarto ....... LEFT JOIN + subconsulta escalar
--   02 Receita recebida por mês ................ JOIN + subconsulta escalar (% do total)
--   03 Parcelas em atraso ...................... 3 JOINs + subconsulta correlacionada
--   04 Serviços com receita acima da média ..... JOIN + HAVING com subconsulta (tabela derivada)
--   05 Quartos disponíveis em um período ....... JOIN + NOT EXISTS correlacionado + parâmetros
--   06 Hóspedes que mais gastaram .............. 3 JOINs + LEFT JOIN em tabela derivada
--   07 Check-ins por funcionário da recepção ... auto-relacionamento + LEFT JOIN + subconsulta
--   08 Reservas acima do ticket médio .......... 4 JOINs + CROSS JOIN com tabela derivada
--   09 Conta parcial das hospedagens abertas ... 4 JOINs + subconsulta correlacionada + tabela derivada
-- ================================================================
SET NAMES utf8mb4; -- garante acentos corretos, qualquer que seja o cliente
USE hotel_db;

-- ================================================================
-- CONSULTA 01 - Ocupação atual por tipo de quarto
-- Mostra, para cada tipo, quantos quartos existem, quantos estão
-- ocupados e a taxa de ocupação, comparando com a ocupação geral.
-- Conceitos: LEFT JOIN (tipos sem quartos também aparecem), CASE,
-- GROUP BY, NULLIF (evita divisão por zero) e subconsulta escalar.
-- ================================================================
SELECT
    tq.nome AS tipo_quarto,
    COUNT(q.numero) AS total_quartos,
    SUM(CASE WHEN q.status = 'OCUPADO' THEN 1 ELSE 0 END) AS quartos_ocupados,
    ROUND(100.0 * SUM(CASE WHEN q.status = 'OCUPADO' THEN 1 ELSE 0 END)
          / NULLIF(COUNT(q.numero), 0), 2) AS taxa_ocupacao_percentual,
    (SELECT ROUND(100.0 * SUM(CASE WHEN q2.status = 'OCUPADO' THEN 1 ELSE 0 END) / COUNT(*), 2)
       FROM QUARTO q2) AS ocupacao_geral_percentual
FROM TIPO_QUARTO tq
LEFT JOIN QUARTO q ON q.id_tipo = tq.id_tipo
GROUP BY tq.id_tipo, tq.nome
ORDER BY taxa_ocupacao_percentual DESC, total_quartos DESC, tq.nome;

-- ================================================================
-- CONSULTA 02 - Receita recebida por mês
-- Soma as parcelas efetivamente pagas, agrupadas pelo mês do
-- pagamento, e calcula a participação de cada mês no total recebido.
-- Conceitos: INNER JOIN, DATE_FORMAT, COUNT DISTINCT, SUM, GROUP BY
-- e subconsulta escalar no SELECT.
-- ================================================================
SELECT
    DATE_FORMAT(pa.data_pagamento, '%Y-%m') AS mes,
    COUNT(DISTINCT pg.id_reserva) AS reservas_pagantes,
    COUNT(*) AS parcelas_pagas,
    SUM(pa.valor) AS receita_recebida,
    ROUND(100.0 * SUM(pa.valor)
          / (SELECT SUM(pa2.valor) FROM PARCELA pa2 WHERE pa2.status = 'PAGA'), 2) AS percentual_do_total
FROM PARCELA pa
INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
WHERE pa.status = 'PAGA'
GROUP BY DATE_FORMAT(pa.data_pagamento, '%Y-%m')
ORDER BY mes;

-- ================================================================
-- CONSULTA 03 - Parcelas em atraso e total em aberto por hóspede
-- Lista parcelas vencidas (ou pendentes com vencimento passado),
-- com hóspede, quarto e dias de atraso. A subconsulta correlacionada
-- soma tudo o que o mesmo hóspede ainda deve, para priorizar a cobrança.
-- Conceitos: múltiplos INNER JOINs, DATEDIFF, filtro composto e
-- subconsulta correlacionada.
-- ================================================================
SELECT
    pe.nome AS hospede,
    r.id_reserva,
    r.numero_quarto,
    pa.numero_parcela,
    pa.data_vencimento,
    pa.valor,
    DATEDIFF(CURRENT_DATE, pa.data_vencimento) AS dias_em_atraso,
    (SELECT SUM(pa2.valor)
       FROM PARCELA pa2
       INNER JOIN PAGAMENTO pg2 ON pg2.id_pagamento = pa2.id_pagamento
       INNER JOIN RESERVA r2 ON r2.id_reserva = pg2.id_reserva
      WHERE r2.cpf_hospede = r.cpf_hospede
        AND pa2.status IN ('PENDENTE', 'VENCIDA')) AS total_em_aberto_hospede
FROM PARCELA pa
INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
INNER JOIN RESERVA r ON r.id_reserva = pg.id_reserva
INNER JOIN PESSOA pe ON pe.cpf = r.cpf_hospede
WHERE pa.status = 'VENCIDA'
   OR (pa.status = 'PENDENTE' AND pa.data_vencimento < CURRENT_DATE)
ORDER BY dias_em_atraso DESC, pe.nome;

-- ================================================================
-- CONSULTA 04 - Serviços com receita acima da média
-- Agrupa os consumos por serviço e mantém apenas os serviços cuja
-- receita supera a receita média por serviço consumido.
-- Conceitos: INNER JOIN, SUM, COUNT DISTINCT, GROUP BY, HAVING com
-- subconsulta e tabela derivada (AVG de uma soma agrupada).
-- ================================================================
SELECT
    s.nome AS servico,
    SUM(cs.quantidade) AS quantidade_consumida,
    COUNT(DISTINCT cs.id_hospedagem) AS hospedagens_atendidas,
    SUM(cs.quantidade * cs.valor_unitario) AS receita_gerada
FROM SERVICO s
INNER JOIN CONSUMO_SERVICO cs ON cs.id_servico = s.id_servico
GROUP BY s.id_servico, s.nome
HAVING SUM(cs.quantidade * cs.valor_unitario) > (
    SELECT AVG(receitas.receita_por_servico)
    FROM (SELECT SUM(cs2.quantidade * cs2.valor_unitario) AS receita_por_servico
            FROM CONSUMO_SERVICO cs2
           GROUP BY cs2.id_servico) AS receitas
)
ORDER BY receita_gerada DESC;

-- ================================================================
-- CONSULTA 05 - Quartos disponíveis em um período
-- Retorna os quartos (fora de manutenção) com capacidade suficiente
-- que não possuem reserva ativa com datas sobrepostas ao período.
-- Duas reservas se sobrepõem quando: entrada_existente < saída_desejada
-- E saída_existente > entrada_desejada. Reservas CANCELADAS não bloqueiam.
-- Conceitos: INNER JOIN, parâmetros, DATEDIFF e NOT EXISTS correlacionado.
-- Na aplicação, os três valores vêm do formulário (PreparedStatement).
-- ================================================================
SET @data_entrada = '2026-09-29';
SET @data_saida = '2026-10-02';
SET @quantidade_hospedes = 2;

SELECT
    q.numero,
    q.andar,
    tq.nome AS tipo_quarto,
    tq.capacidade,
    tq.valor_diaria,
    tq.valor_diaria * DATEDIFF(@data_saida, @data_entrada) AS valor_estimado
FROM QUARTO q
INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
WHERE q.status <> 'MANUTENCAO'
  AND tq.capacidade >= @quantidade_hospedes
  AND NOT EXISTS (
      SELECT 1
      FROM RESERVA r
      WHERE r.numero_quarto = q.numero
        AND r.status IN ('PENDENTE', 'CONFIRMADA', 'EM_HOSPEDAGEM')
        AND r.entrada_prevista < @data_saida
        AND r.saida_prevista > @data_entrada
  )
ORDER BY tq.valor_diaria, q.numero;

-- ================================================================
-- CONSULTA 06 - Hóspedes que mais gastaram (diárias + serviços)
-- Soma o valor das hospedagens realizadas e dos serviços consumidos.
-- Os consumos são pré-agregados por reserva numa tabela derivada para
-- não multiplicar linhas no JOIN com PAGAMENTO (evita somar em dobro).
-- Conceitos: INNER JOINs, LEFT JOIN com tabela derivada, COALESCE,
-- GROUP BY e LIMIT.
-- ================================================================
SELECT
    pe.nome AS hospede,
    COUNT(DISTINCT r.id_reserva) AS hospedagens,
    SUM(pg.valor_total) AS total_diarias,
    COALESCE(SUM(cons.total_servicos), 0) AS total_servicos,
    SUM(pg.valor_total) + COALESCE(SUM(cons.total_servicos), 0) AS gasto_total
FROM HOSPEDE h
INNER JOIN PESSOA pe ON pe.cpf = h.cpf
INNER JOIN RESERVA r ON r.cpf_hospede = h.cpf
INNER JOIN PAGAMENTO pg ON pg.id_reserva = r.id_reserva
LEFT JOIN (
    SELECT hs.id_reserva, SUM(cs.quantidade * cs.valor_unitario) AS total_servicos
    FROM HOSPEDAGEM hs
    INNER JOIN CONSUMO_SERVICO cs ON cs.id_hospedagem = hs.id_hospedagem
    GROUP BY hs.id_reserva
) AS cons ON cons.id_reserva = r.id_reserva
WHERE r.status IN ('FINALIZADA', 'EM_HOSPEDAGEM')
GROUP BY h.cpf, pe.nome
ORDER BY gasto_total DESC
LIMIT 10;

-- ================================================================
-- CONSULTA 07 - Check-ins realizados pela equipe de recepção
-- Mostra cada funcionário da recepção, o nome do seu supervisor
-- (auto-relacionamento FUNCIONARIO -> FUNCIONARIO) e quantos check-ins
-- fez, comparando com a média de check-ins por funcionário.
-- Conceitos: auto-relacionamento (self join), LEFT JOIN, GROUP BY,
-- CASE e subconsulta escalar.
-- ================================================================
SELECT
    pf.nome AS funcionario,
    f.cargo,
    ps.nome AS supervisor,
    COUNT(h.id_hospedagem) AS checkins_realizados,
    CASE
        WHEN COUNT(h.id_hospedagem) > (
            SELECT COUNT(*) / COUNT(DISTINCT h2.cpf_funcionario_checkin)
            FROM HOSPEDAGEM h2
            WHERE h2.cpf_funcionario_checkin IS NOT NULL)
        THEN 'Acima da media'
        ELSE 'Na media ou abaixo'
    END AS desempenho
FROM FUNCIONARIO f
INNER JOIN PESSOA pf ON pf.cpf = f.cpf
LEFT JOIN FUNCIONARIO sup ON sup.cpf = f.cpf_supervisor
LEFT JOIN PESSOA ps ON ps.cpf = sup.cpf
LEFT JOIN HOSPEDAGEM h ON h.cpf_funcionario_checkin = f.cpf
WHERE f.cargo IN ('Recepcionista', 'Supervisor de Recepcao')
GROUP BY f.cpf, pf.nome, f.cargo, ps.nome
ORDER BY checkins_realizados DESC, pf.nome;

-- ================================================================
-- CONSULTA 08 - Reservas com valor acima do ticket médio
-- Calcula o ticket médio das reservas não canceladas numa tabela
-- derivada e lista as reservas acima dele, com hóspede e tipo de quarto.
-- Conceitos: 4 INNER JOINs, CROSS JOIN com tabela derivada, AVG e DATEDIFF.
-- ================================================================
SELECT
    r.id_reserva,
    pe.nome AS hospede,
    tq.nome AS tipo_quarto,
    r.entrada_prevista,
    DATEDIFF(r.saida_prevista, r.entrada_prevista) AS diarias,
    pg.valor_total,
    ROUND(media.ticket_medio, 2) AS ticket_medio,
    ROUND(pg.valor_total - media.ticket_medio, 2) AS valor_acima_da_media
FROM RESERVA r
INNER JOIN PESSOA pe ON pe.cpf = r.cpf_hospede
INNER JOIN QUARTO q ON q.numero = r.numero_quarto
INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
INNER JOIN PAGAMENTO pg ON pg.id_reserva = r.id_reserva
CROSS JOIN (
    SELECT AVG(pg2.valor_total) AS ticket_medio
    FROM PAGAMENTO pg2
    INNER JOIN RESERVA r2 ON r2.id_reserva = pg2.id_reserva
    WHERE r2.status <> 'CANCELADA'
) AS media
WHERE r.status <> 'CANCELADA'
  AND pg.valor_total > media.ticket_medio
ORDER BY pg.valor_total DESC;

-- ================================================================
-- CONSULTA 09 - Conta parcial das hospedagens em aberto
-- Para cada hospedagem aberta, calcula as diárias até hoje e soma os
-- serviços consumidos (subconsulta correlacionada). A tabela derivada
-- permite somar as duas colunas calculadas no SELECT externo.
-- Conceitos: 4 INNER JOINs, DATEDIFF, GREATEST, subconsulta correlacionada
-- e tabela derivada.
-- ================================================================
SELECT
    contas.*,
    contas.valor_diarias + contas.valor_servicos AS total_parcial
FROM (
    SELECT
        hs.id_hospedagem,
        pe.nome AS hospede,
        r.numero_quarto,
        tq.nome AS tipo_quarto,
        DATE(hs.checkin_real) AS data_checkin,
        r.saida_prevista,
        GREATEST(DATEDIFF(CURRENT_DATE, DATE(hs.checkin_real)), 1) AS diarias_ate_hoje,
        GREATEST(DATEDIFF(CURRENT_DATE, DATE(hs.checkin_real)), 1) * tq.valor_diaria AS valor_diarias,
        (SELECT COALESCE(SUM(cs.quantidade * cs.valor_unitario), 0)
           FROM CONSUMO_SERVICO cs
          WHERE cs.id_hospedagem = hs.id_hospedagem) AS valor_servicos
    FROM HOSPEDAGEM hs
    INNER JOIN RESERVA r ON r.id_reserva = hs.id_reserva
    INNER JOIN PESSOA pe ON pe.cpf = r.cpf_hospede
    INNER JOIN QUARTO q ON q.numero = r.numero_quarto
    INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
    WHERE hs.status = 'ABERTA'
) AS contas
ORDER BY total_parcial DESC;
