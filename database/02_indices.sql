-- ================================================================
-- Sistema de Gerenciamento de Hotel - Etapa 04
-- 1. SQL DOS ÍNDICES
-- Integrantes: Arthur Freire e Luís Melo
-- Rodar depois do script de criação e inserção (banco hotel_db).
-- ================================================================
SET NAMES utf8mb4; -- garante acentos corretos, qualquer que seja o cliente
-- Chaves primárias, UNIQUE e chaves estrangeiras já têm índice automático
-- no InnoDB. Os dois índices abaixo cobrem filtros que nenhum desses
-- índices atende por completo: busca por SITUAÇÃO (status) combinada
-- com DATA.
-- ================================================================
USE hotel_db;

-- ÍNDICE 1: parcelas por situação e vencimento
-- O financeiro sempre procura parcelas em aberto ('PENDENTE' ou 'VENCIDA')
-- e compara o vencimento com a data de hoje (atrasada ou a vencer).
-- PARCELA só tinha índice na chave (id_pagamento, numero_parcela), então
-- esse filtro lia a tabela inteira. Com (status, data_vencimento) o MySQL
-- vai direto às parcelas em aberto, já ordenadas por vencimento.
-- Usado em: visão vw_contas_a_receber e consulta 4 (subconsultas).
CREATE INDEX idx_parcela_status_vencimento
    ON PARCELA (status, data_vencimento);

-- ÍNDICE 2: reservas de um hóspede por situação e data de entrada
-- Na recepção e na cobrança, a pergunta é sempre sobre as reservas de UM
-- hóspede numa situação: "ele tem reserva futura (PENDENTE/CONFIRMADA)
-- a partir de hoje?". O índice da FK só tinha o cpf_hospede; com
-- (cpf_hospede, status, entrada_prevista) o MySQL acha as reservas do
-- hóspede já filtradas por situação e data, sem ler as outras.
-- Usado em: consulta 4 (na consulta externa e na subconsulta escalar).
CREATE INDEX idx_reserva_hospede_status_entrada
    ON RESERVA (cpf_hospede, status, entrada_prevista);

-- ----------------------------------------------------------------
-- Conferência: o EXPLAIN mostra o índice na coluna "key".
-- ----------------------------------------------------------------
-- Índice 1 (key = idx_parcela_status_vencimento):
EXPLAIN SELECT id_pagamento, numero_parcela, data_vencimento, valor
FROM PARCELA
WHERE status IN ('PENDENTE', 'VENCIDA')
  AND data_vencimento < CURRENT_DATE;

-- Índice 2 (key = idx_reserva_hospede_status_entrada):
EXPLAIN SELECT id_reserva, numero_quarto, entrada_prevista
FROM RESERVA
WHERE cpf_hospede = '10000000007'
  AND status IN ('PENDENTE', 'CONFIRMADA')
  AND entrada_prevista >= CURRENT_DATE;

-- Na consulta 4 (arquivo de consultas), o EXPLAIN mostra os dois índices.
