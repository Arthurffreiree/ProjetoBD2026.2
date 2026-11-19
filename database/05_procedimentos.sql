-- ================================================================
-- Sistema de Gerenciamento de Hotel - Etapa 05
-- 2. SQL DOS PROCEDIMENTOS
-- Integrantes: Arthur Freire e Luís Melo
-- Rodar depois do script de criação e inserção e do script de funções
-- (o procedimento 1 usa a função fn_valor_conta).
-- ================================================================
SET NAMES utf8mb4; -- garante acentos corretos, qualquer que seja o cliente
USE hotel_db;

DROP PROCEDURE IF EXISTS sp_realizar_checkout;
DROP PROCEDURE IF EXISTS sp_registrar_pagamento_hospede;

DELIMITER $$

-- ----------------------------------------------------------------
-- PROCEDIMENTO 1 (ATUALIZAÇÃO DE DADOS): sp_realizar_checkout
-- ----------------------------------------------------------------
-- Justificativa: o check-out mexe em três tabelas ao mesmo tempo e elas
-- precisam ficar coerentes entre si:
--   HOSPEDAGEM -> registra a hora de saída e passa para ENCERRADA
--   RESERVA    -> passa para FINALIZADA
--   QUARTO     -> passa para LIMPEZA (a governança libera depois)
-- Tudo roda numa transação: se qualquer UPDATE falhar, nada é gravado.
-- Antes, o procedimento confere se a hospedagem existe e está ABERTA.
-- No fim, mostra o resumo com o valor da conta (fn_valor_conta).
CREATE PROCEDURE sp_realizar_checkout(IN p_id_hospedagem INT)
BEGIN
    DECLARE v_status     VARCHAR(20);
    DECLARE v_id_reserva INT;
    DECLARE v_quarto     VARCHAR(10);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SELECT h.status, h.id_reserva, r.numero_quarto
      INTO v_status, v_id_reserva, v_quarto
      FROM HOSPEDAGEM h
      INNER JOIN RESERVA r ON r.id_reserva = h.id_reserva
     WHERE h.id_hospedagem = p_id_hospedagem;

    IF v_status IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Hospedagem não encontrada.';
    ELSEIF v_status <> 'ABERTA' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Check-out já realizado para esta hospedagem.';
    END IF;

    START TRANSACTION;

    UPDATE HOSPEDAGEM
       SET checkout_real = NOW(),
           status = 'ENCERRADA'
     WHERE id_hospedagem = p_id_hospedagem;

    UPDATE RESERVA
       SET status = 'FINALIZADA'
     WHERE id_reserva = v_id_reserva;

    UPDATE QUARTO
       SET status = 'LIMPEZA'
     WHERE numero = v_quarto;

    COMMIT;

    SELECT p_id_hospedagem             AS id_hospedagem,
           v_id_reserva                AS id_reserva,
           v_quarto                    AS quarto,
           fn_valor_conta(v_id_reserva) AS valor_conta,
           'Check-out realizado'       AS mensagem;
END$$

-- ----------------------------------------------------------------
-- PROCEDIMENTO 2 (COM CURSOR): sp_registrar_pagamento_hospede
-- ----------------------------------------------------------------
-- Justificativa: o hóspede chega no caixa e paga um valor qualquer
-- (ex.: R$ 2.000). A regra do hotel é quitar as parcelas em aberto
-- dele da MAIS ANTIGA para a mais nova, uma parcela inteira por vez,
-- e parar quando o dinheiro não cobre a próxima. O que sobra volta
-- como saldo (troco ou crédito).
-- Por que precisa de CURSOR (e não dá com um UPDATE só):
--   - a decisão de cada parcela depende do SALDO que sobrou depois das
--     parcelas anteriores, ou seja, as linhas têm que ser processadas
--     em ordem, uma de cada vez, guardando o saldo numa variável;
--   - o processo PARA na primeira parcela que o saldo não cobre, mesmo
--     que parcelas seguintes sejam menores (não se pula a dívida antiga);
--   - a cada parcela quitada, o status do PAGAMENTO correspondente é
--     recalculado (PAGO se não sobrou parcela aberta, senão PARCIAL).
-- Parâmetros: CPF do hóspede, valor entregue; OUT = saldo que sobrou.
CREATE PROCEDURE sp_registrar_pagamento_hospede(
    IN  p_cpf   CHAR(11),
    IN  p_valor DECIMAL(10,2),
    OUT p_saldo DECIMAL(10,2)
)
BEGIN
    DECLARE v_fim           BOOLEAN DEFAULT FALSE;
    DECLARE v_id_pagamento  INT;
    DECLARE v_numero        SMALLINT;
    DECLARE v_valor_parcela DECIMAL(10,2);
    DECLARE v_quitadas      INT DEFAULT 0;

    -- parcelas em aberto do hóspede, da mais antiga para a mais nova
    DECLARE cur_parcelas CURSOR FOR
        SELECT pa.id_pagamento, pa.numero_parcela, pa.valor
          FROM PARCELA pa
          INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
          INNER JOIN RESERVA r    ON r.id_reserva = pg.id_reserva
         WHERE r.cpf_hospede = p_cpf
           AND pa.status IN ('PENDENTE', 'VENCIDA')
         ORDER BY pa.data_vencimento, pa.id_pagamento, pa.numero_parcela;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_fim = TRUE;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF p_valor IS NULL OR p_valor <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O valor pago deve ser maior que zero.';
    END IF;

    SET p_saldo = p_valor;

    START TRANSACTION;
    OPEN cur_parcelas;

    percorre: LOOP
        FETCH cur_parcelas INTO v_id_pagamento, v_numero, v_valor_parcela;
        IF v_fim THEN
            LEAVE percorre;
        END IF;

        -- o saldo não cobre a parcela mais antiga em aberto: para aqui
        IF p_saldo < v_valor_parcela THEN
            LEAVE percorre;
        END IF;

        UPDATE PARCELA
           SET status = 'PAGA',
               data_pagamento = NOW()
         WHERE id_pagamento = v_id_pagamento
           AND numero_parcela = v_numero;

        UPDATE PAGAMENTO
           SET status = IF(EXISTS (SELECT 1 FROM PARCELA x
                                    WHERE x.id_pagamento = v_id_pagamento
                                      AND x.status IN ('PENDENTE', 'VENCIDA')),
                           'PARCIAL', 'PAGO')
         WHERE id_pagamento = v_id_pagamento;

        SET p_saldo = p_saldo - v_valor_parcela;
        SET v_quitadas = v_quitadas + 1;
    END LOOP;

    CLOSE cur_parcelas;
    COMMIT;

    SELECT p_cpf      AS cpf_hospede,
           p_valor    AS valor_recebido,
           v_quitadas AS parcelas_quitadas,
           p_saldo    AS saldo_restante;
END$$

DELIMITER ;

-- ----------------------------------------------------------------
-- Exemplos de uso
-- ----------------------------------------------------------------
-- Check-out da hospedagem 39 (quarto 114):
-- CALL sp_realizar_checkout(39);

-- Sabrina (CPF 10000000019) paga R$ 2.000 no caixa:
-- CALL sp_registrar_pagamento_hospede('10000000019', 2000.00, @saldo);
-- SELECT @saldo;
