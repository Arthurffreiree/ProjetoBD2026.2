-- ================================================================
-- Sistema de Gerenciamento de Hotel - Etapa 05
-- 3. SQL DOS TRIGGERS
-- Integrantes: Arthur Freire e Luís Melo
-- Rodar depois do script de criação e inserção (banco hotel_db).
-- ================================================================
SET NAMES utf8mb4; -- garante acentos corretos, qualquer que seja o cliente
USE hotel_db;

DROP TRIGGER IF EXISTS trg_reserva_log_status;
DROP TRIGGER IF EXISTS trg_reserva_valida_insert;
DROP TRIGGER IF EXISTS trg_reserva_valida_update;
DROP TRIGGER IF EXISTS trg_funcionario_supervisor_insert;
DROP TRIGGER IF EXISTS trg_funcionario_supervisor_update;

-- ----------------------------------------------------------------
-- Tabela de log usada pelo trigger 1
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS LOG_RESERVA (
    id_log          INT          PRIMARY KEY AUTO_INCREMENT,
    id_reserva      INT          NOT NULL,
    status_anterior VARCHAR(20)  NOT NULL,
    status_novo     VARCHAR(20)  NOT NULL,
    data_hora       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario         VARCHAR(100) NOT NULL,
    CONSTRAINT fk_log_reserva FOREIGN KEY (id_reserva) REFERENCES RESERVA (id_reserva)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

DELIMITER $$

-- ----------------------------------------------------------------
-- TRIGGER 1 (ATUALIZA TABELA DE LOG): trg_reserva_log_status
-- ----------------------------------------------------------------
-- Justificativa: a situação da reserva decide dinheiro e ocupação
-- (confirmar, cancelar, fazer check-in, finalizar). Quando há
-- reclamação ou divergência ("quem cancelou minha reserva?"), o hotel
-- precisa do histórico. Toda vez que o status de uma reserva muda, o
-- trigger grava em LOG_RESERVA o status anterior, o novo, a hora e o
-- usuário do banco que fez a alteração. Funciona para qualquer origem
-- da mudança: tela, procedimento (ex.: sp_realizar_checkout) ou SQL direto.
CREATE TRIGGER trg_reserva_log_status
AFTER UPDATE ON RESERVA
FOR EACH ROW
BEGIN
    IF OLD.status <> NEW.status THEN
        INSERT INTO LOG_RESERVA (id_reserva, status_anterior, status_novo, usuario)
        VALUES (NEW.id_reserva, OLD.status, NEW.status, CURRENT_USER());
    END IF;
END$$

-- ----------------------------------------------------------------
-- TRIGGER 2 (REGRA DE NEGÓCIO): trg_reserva_valida_insert
-- ----------------------------------------------------------------
-- Justificativa: duas regras básicas de um hotel que as constraints
-- não conseguem garantir, porque dependem de OUTRAS linhas e tabelas:
--   1) o número de hóspedes não pode passar da capacidade do tipo do quarto;
--   2) o quarto não pode ter duas reservas ativas no mesmo período
--      (overbooking). Reservas CANCELADAS ou FINALIZADAS não contam.
-- Se alguma regra for violada, o INSERT é recusado com uma mensagem.
CREATE TRIGGER trg_reserva_valida_insert
BEFORE INSERT ON RESERVA
FOR EACH ROW
BEGIN
    DECLARE v_capacidade SMALLINT;

    SELECT tq.capacidade
      INTO v_capacidade
      FROM QUARTO q
      INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
     WHERE q.numero = NEW.numero_quarto;

    IF NEW.qtd_hospedes > v_capacidade THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Quantidade de hóspedes maior que a capacidade do quarto.';
    END IF;

    IF NEW.status IN ('PENDENTE', 'CONFIRMADA', 'EM_HOSPEDAGEM')
       AND EXISTS (SELECT 1
                     FROM RESERVA r
                    WHERE r.numero_quarto = NEW.numero_quarto
                      AND r.status IN ('PENDENTE', 'CONFIRMADA', 'EM_HOSPEDAGEM')
                      AND r.entrada_prevista < NEW.saida_prevista
                      AND r.saida_prevista > NEW.entrada_prevista) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'O quarto já tem reserva ativa nesse período.';
    END IF;
END$$

-- ----------------------------------------------------------------
-- TRIGGER 2b: trg_reserva_valida_update (versão final, Etapa 06)
-- ----------------------------------------------------------------
-- A tela de reservas também permite ALTERAR quarto, datas e hóspedes.
-- Sem esta versão para UPDATE, uma alteração poderia furar as mesmas regras
-- do trigger 2. A diferença é ignorar a própria reserva (id diferente) na
-- busca por conflito de período.
CREATE TRIGGER trg_reserva_valida_update
BEFORE UPDATE ON RESERVA
FOR EACH ROW
BEGIN
    DECLARE v_capacidade SMALLINT;

    SELECT tq.capacidade
      INTO v_capacidade
      FROM QUARTO q
      INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
     WHERE q.numero = NEW.numero_quarto;

    IF NEW.qtd_hospedes > v_capacidade THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Quantidade de hóspedes maior que a capacidade do quarto.';
    END IF;

    IF NEW.status IN ('PENDENTE', 'CONFIRMADA', 'EM_HOSPEDAGEM')
       AND EXISTS (SELECT 1
                     FROM RESERVA r
                    WHERE r.numero_quarto = NEW.numero_quarto
                      AND r.id_reserva <> NEW.id_reserva
                      AND r.status IN ('PENDENTE', 'CONFIRMADA', 'EM_HOSPEDAGEM')
                      AND r.entrada_prevista < NEW.saida_prevista
                      AND r.saida_prevista > NEW.entrada_prevista) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'O quarto já tem reserva ativa nesse período.';
    END IF;
END$$

-- ----------------------------------------------------------------
-- EXTRA (pendência da Etapa 02): funcionário não supervisiona a si mesmo
-- ----------------------------------------------------------------
-- O CHECK (cpf_supervisor <> cpf) foi retirado do script de criação
-- porque o MySQL 8 não aceita CHECK em coluna de FK com ação referencial
-- (erro 3823). A regra passa a ser garantida por estes dois triggers.
CREATE TRIGGER trg_funcionario_supervisor_insert
BEFORE INSERT ON FUNCIONARIO
FOR EACH ROW
BEGIN
    IF NEW.cpf_supervisor = NEW.cpf THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Um funcionário não pode ser supervisor de si mesmo.';
    END IF;
END$$

CREATE TRIGGER trg_funcionario_supervisor_update
BEFORE UPDATE ON FUNCIONARIO
FOR EACH ROW
BEGIN
    IF NEW.cpf_supervisor = NEW.cpf THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Um funcionário não pode ser supervisor de si mesmo.';
    END IF;
END$$

DELIMITER ;

-- ----------------------------------------------------------------
-- Testes
-- ----------------------------------------------------------------
-- Trigger 1: cancelar uma reserva e ver o log
-- UPDATE RESERVA SET status = 'CANCELADA' WHERE id_reserva = 54;
-- SELECT * FROM LOG_RESERVA;

-- Trigger 2: reserva para 5 pessoas num quarto de solteiro (recusada)
-- INSERT INTO RESERVA (cpf_hospede, numero_quarto, entrada_prevista, saida_prevista, qtd_hospedes, status)
-- VALUES ('10000000001', '101', '2027-02-01', '2027-02-03', 5, 'PENDENTE');

-- Trigger 2: quarto 115 já reservado de 16/11 a 20/11/2026 (reserva 35)
-- Recusada (choca com o período):
-- INSERT INTO RESERVA (cpf_hospede, numero_quarto, entrada_prevista, saida_prevista, qtd_hospedes, status)
-- VALUES ('10000000001', '115', '2026-11-18', '2026-11-22', 1, 'PENDENTE');
-- Aceita (entra no dia em que o outro hóspede sai):
-- INSERT INTO RESERVA (cpf_hospede, numero_quarto, entrada_prevista, saida_prevista, qtd_hospedes, status)
-- VALUES ('10000000001', '115', '2026-11-20', '2026-11-22', 1, 'PENDENTE');

-- Extra: funcionário como supervisor de si mesmo (recusado)
-- UPDATE FUNCIONARIO SET cpf_supervisor = cpf WHERE cpf = '20000000006';
