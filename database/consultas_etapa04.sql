-- ================================================================
-- Sistema de Gerenciamento de Hotel - Etapa 04
-- 2. SQL DAS CONSULTAS
-- Integrantes: Arthur Freire e Luís Melo
-- Rodar depois do script de criação e inserção e do script de índices.
-- ================================================================
SET NAMES utf8mb4; -- garante acentos corretos, qualquer que seja o cliente
USE hotel_db;

-- ----------------------------------------------------------------
-- CONSULTA 1 (ANTI JOIN À ESQUERDA): quartos que nunca foram reservados
-- ----------------------------------------------------------------
-- Pergunta: quais quartos nunca receberam nenhuma reserva? A gerência
-- usa essa lista para revisar preço, divulgação ou a própria categoria.
-- Como funciona: o LEFT JOIN mantém todos os quartos; quem não encontrou
-- reserva fica com as colunas de RESERVA nulas, e o WHERE ... IS NULL
-- deixa só esses (é o "anti join": as linhas da esquerda SEM par).
SELECT
    q.numero,
    q.andar,
    tq.nome AS tipo_quarto,
    tq.capacidade,
    tq.valor_diaria,
    q.status
FROM QUARTO q
INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
LEFT JOIN RESERVA r       ON r.numero_quarto = q.numero
WHERE r.id_reserva IS NULL
ORDER BY tq.valor_diaria DESC, q.numero;

-- ----------------------------------------------------------------
-- CONSULTA 2 (FULL OUTER JOIN): papel de cada pessoa no hotel
-- ----------------------------------------------------------------
-- Pergunta: entre hóspedes e funcionários, quem é só hóspede, quem é só
-- funcionário e quem é os dois (funcionário que também se hospedou)?
-- O MySQL não tem FULL OUTER JOIN; ele é emulado com
-- LEFT JOIN  UNION  RIGHT JOIN:
--   - o LEFT JOIN traz todos os hóspedes (com ou sem funcionário igual);
--   - o RIGHT JOIN traz todos os funcionários (com ou sem hóspede igual);
--   - o UNION junta os dois e remove a linha repetida de quem está nos dois.
SELECT
    p.nome,
    p.cpf,
    CASE
        WHEN completo.cpf_hospede IS NOT NULL
         AND completo.cpf_funcionario IS NOT NULL THEN 'HOSPEDE E FUNCIONARIO'
        WHEN completo.cpf_hospede IS NOT NULL      THEN 'SO HOSPEDE'
        ELSE 'SO FUNCIONARIO'
    END AS papel,
    completo.cargo,
    completo.cliente_desde
FROM (
    SELECT h.cpf AS cpf_hospede, f.cpf AS cpf_funcionario, f.cargo, h.data_cadastro AS cliente_desde
    FROM HOSPEDE h
    LEFT JOIN FUNCIONARIO f ON f.cpf = h.cpf
    UNION
    SELECT h.cpf AS cpf_hospede, f.cpf AS cpf_funcionario, f.cargo, h.data_cadastro AS cliente_desde
    FROM HOSPEDE h
    RIGHT JOIN FUNCIONARIO f ON f.cpf = h.cpf
) AS completo
INNER JOIN PESSOA p ON p.cpf = COALESCE(completo.cpf_hospede, completo.cpf_funcionario)
ORDER BY papel, p.nome;

-- ----------------------------------------------------------------
-- CONSULTA 3 (SUBCONSULTA CORRELACIONADA): quartos que faturam acima
-- da média do próprio andar
-- ----------------------------------------------------------------
-- Pergunta: em cada andar, quais quartos renderam mais que a média dos
-- quartos daquele andar? Mostra as unidades que puxam a receita.
-- Como funciona: o WITH (CTE) calcula uma vez a receita de cada quarto
-- (pagamentos de reservas não canceladas; quarto sem reserva = 0).
-- As subconsultas correlacionadas recalculam a média só dos quartos do
-- MESMO andar do quarto atual (r2.andar = r1.andar).
WITH receita_quarto AS (
    SELECT q.numero, q.andar, tq.nome AS tipo_quarto,
           COALESCE(SUM(pg.valor_total), 0) AS receita
    FROM QUARTO q
    INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
    LEFT JOIN RESERVA rs      ON rs.numero_quarto = q.numero
                             AND rs.status <> 'CANCELADA'
    LEFT JOIN PAGAMENTO pg    ON pg.id_reserva = rs.id_reserva
    GROUP BY q.numero, q.andar, tq.nome
)
SELECT
    r1.andar,
    r1.numero AS quarto,
    r1.tipo_quarto,
    r1.receita,
    (SELECT ROUND(AVG(r2.receita), 2)
       FROM receita_quarto r2
      WHERE r2.andar = r1.andar) AS media_do_andar
FROM receita_quarto r1
WHERE r1.receita > (SELECT AVG(r2.receita)
                      FROM receita_quarto r2
                     WHERE r2.andar = r1.andar)
ORDER BY r1.andar, r1.receita DESC;

-- ----------------------------------------------------------------
-- CONSULTA 4 (SUBCONSULTA COM IN): reservas futuras de hóspedes que
-- têm parcela em atraso
-- ----------------------------------------------------------------
-- Pergunta: quais hóspedes têm reserva nos próximos dias e ainda devem
-- parcelas vencidas de estadias anteriores? A recepção cobra antes do
-- check-in.
-- Como funciona: a subconsulta do IN lista os CPFs com parcela em atraso
-- ('VENCIDA', ou 'PENDENTE' com vencimento já passado); a consulta externa
-- pega as reservas futuras desses CPFs. A subconsulta escalar soma o
-- valor atrasado de cada hóspede.
-- Índices usados (confira com EXPLAIN): idx_parcela_status_vencimento
-- (filtro das parcelas em atraso) e idx_reserva_hospede_status_entrada
-- (reservas futuras de cada hóspede e soma do atraso).
SELECT
    p.nome AS hospede,
    r.id_reserva,
    r.numero_quarto,
    r.entrada_prevista,
    r.status,
    (SELECT SUM(pa.valor)
       FROM PARCELA pa
       INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
       INNER JOIN RESERVA r2   ON r2.id_reserva = pg.id_reserva
      WHERE r2.cpf_hospede = r.cpf_hospede
        AND pa.status IN ('PENDENTE', 'VENCIDA')
        AND pa.data_vencimento < CURRENT_DATE) AS valor_em_atraso
FROM RESERVA r
INNER JOIN PESSOA p ON p.cpf = r.cpf_hospede
WHERE r.status IN ('PENDENTE', 'CONFIRMADA')
  AND r.entrada_prevista >= CURRENT_DATE
  AND r.cpf_hospede IN (SELECT r3.cpf_hospede
                          FROM PARCELA pa
                          INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
                          INNER JOIN RESERVA r3   ON r3.id_reserva = pg.id_reserva
                         WHERE pa.status IN ('PENDENTE', 'VENCIDA')
                           AND pa.data_vencimento < CURRENT_DATE)
ORDER BY r.entrada_prevista, p.nome;
