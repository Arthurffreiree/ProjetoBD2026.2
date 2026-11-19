-- ================================================================
-- Sistema de Gerenciamento de Hotel - Etapa 04
-- 3. SQL DAS VISÕES
-- Integrantes: Arthur Freire e Luís Melo
-- Rodar depois do script de criação e inserção (banco hotel_db).
-- ================================================================
SET NAMES utf8mb4; -- garante acentos corretos, qualquer que seja o cliente
USE hotel_db;

-- ----------------------------------------------------------------
-- VISÃO 1: vw_reserva_completa
-- ----------------------------------------------------------------
-- Justificativa semântica: para a recepção, uma reserva não é uma linha
-- da tabela RESERVA, é "quem vem, para qual quarto, quantas diárias,
-- quanto custa e quanto já pagou". Essas informações estão espalhadas em
-- 6 tabelas. A visão monta essa ficha completa uma vez só e serve de
-- base para atendimento e relatórios, sem repetir os joins.
-- Joins: RESERVA, HOSPEDE, PESSOA, QUARTO, TIPO_QUARTO (INNER JOIN) e
-- PAGAMENTO (LEFT JOIN: uma reserva recém-criada pode não ter cobrança).
CREATE OR REPLACE VIEW vw_reserva_completa AS
SELECT
    r.id_reserva,
    r.status                                        AS status_reserva,
    p.cpf                                           AS cpf_hospede,
    p.nome                                          AS hospede,
    q.numero                                        AS quarto,
    q.andar,
    tq.nome                                         AS tipo_quarto,
    r.qtd_hospedes,
    r.entrada_prevista,
    r.saida_prevista,
    DATEDIFF(r.saida_prevista, r.entrada_prevista)  AS diarias,
    tq.valor_diaria,
    pg.status                                       AS status_pagamento,
    COALESCE(pg.valor_total, 0)                     AS valor_total,
    COALESCE((SELECT SUM(pa.valor)
                FROM PARCELA pa
               WHERE pa.id_pagamento = pg.id_pagamento
                 AND pa.status = 'PAGA'), 0)        AS valor_pago
FROM RESERVA r
INNER JOIN HOSPEDE h      ON h.cpf = r.cpf_hospede
INNER JOIN PESSOA p       ON p.cpf = h.cpf
INNER JOIN QUARTO q       ON q.numero = r.numero_quarto
INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
LEFT JOIN PAGAMENTO pg    ON pg.id_reserva = r.id_reserva;

-- ----------------------------------------------------------------
-- VISÃO 2: vw_contas_a_receber
-- ----------------------------------------------------------------
-- Justificativa semântica: é a lista de trabalho do setor financeiro.
-- Mostra cada parcela ainda não paga, de quem cobrar (nome e e-mail),
-- a qual reserva e quarto ela se refere e a situação: ATRASADA (o
-- vencimento já passou) ou A VENCER, com os dias de atraso. O setor
-- consulta a cobrança sem precisar saber em que tabelas está cada dado.
-- Joins: PARCELA, PAGAMENTO, RESERVA, PESSOA, QUARTO (INNER JOIN).
-- Usa o índice idx_parcela_status_vencimento no filtro de status.
CREATE OR REPLACE VIEW vw_contas_a_receber AS
SELECT
    pa.id_pagamento,
    pa.numero_parcela,
    r.id_reserva,
    p.nome                                       AS hospede,
    p.email,
    q.numero                                     AS quarto,
    pg.forma_pagamento,
    pa.data_vencimento,
    pa.valor,
    CASE
        WHEN pa.data_vencimento < CURRENT_DATE THEN 'ATRASADA'
        ELSE 'A VENCER'
    END                                          AS situacao,
    GREATEST(DATEDIFF(CURRENT_DATE, pa.data_vencimento), 0) AS dias_em_atraso
FROM PARCELA pa
INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
INNER JOIN RESERVA r    ON r.id_reserva = pg.id_reserva
INNER JOIN PESSOA p     ON p.cpf = r.cpf_hospede
INNER JOIN QUARTO q     ON q.numero = r.numero_quarto
WHERE pa.status IN ('PENDENTE', 'VENCIDA');

-- ----------------------------------------------------------------
-- Exemplos de uso
-- ----------------------------------------------------------------
-- Reservas que ainda têm saldo a pagar:
SELECT id_reserva, hospede, quarto, status_reserva, valor_total, valor_pago,
       valor_total - valor_pago AS saldo
FROM vw_reserva_completa
WHERE status_reserva <> 'CANCELADA'
  AND valor_pago < valor_total
ORDER BY saldo DESC;

-- Cobranças atrasadas, das mais antigas para as mais novas:
SELECT hospede, email, quarto, data_vencimento, valor, dias_em_atraso
FROM vw_contas_a_receber
WHERE situacao = 'ATRASADA'
ORDER BY dias_em_atraso DESC;
