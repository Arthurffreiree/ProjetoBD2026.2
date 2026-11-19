-- ================================================================
-- Sistema de Gerenciamento de Hotel - Etapa 05
-- 1. SQL DAS FUNÇÕES
-- Integrantes: Arthur Freire e Luís Melo
-- Rodar depois do script de criação e inserção (banco hotel_db).
-- ================================================================
SET NAMES utf8mb4; -- garante acentos corretos, qualquer que seja o cliente
USE hotel_db;

DROP FUNCTION IF EXISTS fn_valor_conta;
DROP FUNCTION IF EXISTS fn_categoria_hospede;

DELIMITER $$

-- ----------------------------------------------------------------
-- FUNÇÃO 1: fn_valor_conta(id_reserva)
-- ----------------------------------------------------------------
-- Justificativa: "quanto essa reserva custa no total?" é a pergunta
-- mais comum da recepção e do financeiro (no check-out, na cobrança,
-- nos relatórios). O valor é a soma das diárias (dias x valor da
-- diária do tipo do quarto) com os serviços consumidos na hospedagem.
-- Como a regra fica numa função, todo lugar que precisa do total usa
-- o mesmo cálculo. Retorna NULL se a reserva não existir.
CREATE FUNCTION fn_valor_conta(p_id_reserva INT)
RETURNS DECIMAL(10,2)
READS SQL DATA
BEGIN
    DECLARE v_diarias   DECIMAL(10,2);
    DECLARE v_servicos  DECIMAL(10,2);

    SELECT DATEDIFF(r.saida_prevista, r.entrada_prevista) * tq.valor_diaria
      INTO v_diarias
      FROM RESERVA r
      INNER JOIN QUARTO q       ON q.numero = r.numero_quarto
      INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
     WHERE r.id_reserva = p_id_reserva;

    SELECT COALESCE(SUM(c.quantidade * c.valor_unitario), 0)
      INTO v_servicos
      FROM HOSPEDAGEM h
      INNER JOIN CONSUMO_SERVICO c ON c.id_hospedagem = h.id_hospedagem
     WHERE h.id_reserva = p_id_reserva;

    RETURN v_diarias + v_servicos;
END$$

-- ----------------------------------------------------------------
-- FUNÇÃO 2: fn_categoria_hospede(cpf)  (usa estrutura condicional)
-- ----------------------------------------------------------------
-- Justificativa: o hotel tem programa de fidelidade. A categoria do
-- hóspede depende de quanto ele já pagou (parcelas PAGA de todas as
-- reservas dele) e é usada para dar benefícios (upgrade, late checkout)
-- e para tratamento na recepção. A regra de faixas fica num lugar só:
--   OURO    pagou R$ 6.000 ou mais
--   PRATA   pagou de R$ 3.000 a R$ 5.999,99
--   BRONZE  pagou alguma coisa, abaixo de R$ 3.000
--   NOVO    ainda não pagou nada (ou não existe como hóspede)
CREATE FUNCTION fn_categoria_hospede(p_cpf CHAR(11))
RETURNS VARCHAR(10)
READS SQL DATA
BEGIN
    DECLARE v_total_pago DECIMAL(12,2);

    SELECT COALESCE(SUM(pa.valor), 0)
      INTO v_total_pago
      FROM RESERVA r
      INNER JOIN PAGAMENTO pg ON pg.id_reserva = r.id_reserva
      INNER JOIN PARCELA pa   ON pa.id_pagamento = pg.id_pagamento
     WHERE r.cpf_hospede = p_cpf
       AND pa.status = 'PAGA';

    IF v_total_pago >= 6000 THEN
        RETURN 'OURO';
    ELSEIF v_total_pago >= 3000 THEN
        RETURN 'PRATA';
    ELSEIF v_total_pago > 0 THEN
        RETURN 'BRONZE';
    ELSE
        RETURN 'NOVO';
    END IF;
END$$

DELIMITER ;

-- ----------------------------------------------------------------
-- Exemplos de uso
-- ----------------------------------------------------------------
-- Valor total da conta das hospedagens em andamento:
SELECT r.id_reserva, r.numero_quarto, fn_valor_conta(r.id_reserva) AS valor_conta
FROM RESERVA r
WHERE r.status = 'EM_HOSPEDAGEM'
ORDER BY valor_conta DESC;

-- Categoria de fidelidade de cada hóspede:
SELECT p.nome, fn_categoria_hospede(h.cpf) AS categoria
FROM HOSPEDE h
INNER JOIN PESSOA p ON p.cpf = h.cpf
ORDER BY FIELD(categoria, 'OURO', 'PRATA', 'BRONZE', 'NOVO'), p.nome;
