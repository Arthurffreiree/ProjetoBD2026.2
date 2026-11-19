package br.edu.cesar.hotel.dao;

import br.edu.cesar.hotel.util.ConnectionFactory;
import br.edu.cesar.hotel.util.JdbcUtils;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Integração com as rotinas da Etapa 05:
 *   funções       fn_valor_conta e fn_categoria_hospede (usadas dentro de SELECTs)
 *   procedimentos sp_realizar_checkout e sp_registrar_pagamento_hospede (CallableStatement)
 *   triggers      efeito visível na tabela LOG_RESERVA
 */
public class RotinaDAO {

    /** fn_categoria_hospede: categoria de fidelidade de cada hóspede. */
    public List<Map<String, Object>> categoriasHospedes() throws SQLException {
        return JdbcUtils.consultar("""
                SELECT p.nome AS hospede,
                       h.cpf,
                       fn_categoria_hospede(h.cpf) AS categoria,
                       COALESCE((SELECT SUM(pa.valor)
                                   FROM RESERVA r
                                   INNER JOIN PAGAMENTO pg ON pg.id_reserva = r.id_reserva
                                   INNER JOIN PARCELA pa   ON pa.id_pagamento = pg.id_pagamento
                                  WHERE r.cpf_hospede = h.cpf AND pa.status = 'PAGA'), 0) AS total_pago
                FROM HOSPEDE h
                INNER JOIN PESSOA p ON p.cpf = h.cpf
                ORDER BY total_pago DESC, p.nome
                """);
    }

    /** fn_valor_conta: valor da conta de cada hospedagem em andamento. */
    public List<Map<String, Object>> hospedagensAbertas() throws SQLException {
        return JdbcUtils.consultar("""
                SELECT h.id_hospedagem,
                       r.id_reserva,
                       p.nome AS hospede,
                       r.numero_quarto AS quarto,
                       DATE(h.checkin_real) AS checkin,
                       r.saida_prevista,
                       fn_valor_conta(r.id_reserva) AS valor_conta
                FROM HOSPEDAGEM h
                INNER JOIN RESERVA r ON r.id_reserva = h.id_reserva
                INNER JOIN PESSOA p  ON p.cpf = r.cpf_hospede
                WHERE h.status = 'ABERTA'
                ORDER BY r.saida_prevista, r.numero_quarto
                """);
    }

    /** CALL sp_realizar_checkout(?): devolve a linha de resumo do procedimento. */
    public Map<String, Object> realizarCheckout(int idHospedagem) throws SQLException {
        try (Connection conexao = ConnectionFactory.getConnection();
             CallableStatement chamada = conexao.prepareCall("{CALL sp_realizar_checkout(?)}")) {
            chamada.setInt(1, idHospedagem);
            return primeiraLinha(chamada);
        }
    }

    /** CALL sp_registrar_pagamento_hospede(?, ?, ?): o 3º parâmetro é OUT (saldo restante). */
    public Map<String, Object> registrarPagamento(String cpf, BigDecimal valor) throws SQLException {
        try (Connection conexao = ConnectionFactory.getConnection();
             CallableStatement chamada = conexao.prepareCall("{CALL sp_registrar_pagamento_hospede(?, ?, ?)}")) {
            chamada.setString(1, cpf);
            chamada.setBigDecimal(2, valor);
            chamada.registerOutParameter(3, Types.DECIMAL);
            Map<String, Object> resumo = primeiraLinha(chamada);
            resumo.put("saldo_out", chamada.getBigDecimal(3));
            return resumo;
        }
    }

    /** Hóspedes com parcelas em aberto (opções do formulário de pagamento). */
    public List<Map<String, Object>> devedores() throws SQLException {
        return JdbcUtils.consultar("""
                SELECT r.cpf_hospede AS cpf, p.nome, COUNT(*) AS parcelas_abertas, SUM(pa.valor) AS total_aberto
                FROM PARCELA pa
                INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
                INNER JOIN RESERVA r    ON r.id_reserva = pg.id_reserva
                INNER JOIN PESSOA p     ON p.cpf = r.cpf_hospede
                WHERE pa.status IN ('PENDENTE', 'VENCIDA')
                GROUP BY r.cpf_hospede, p.nome
                ORDER BY p.nome
                """);
    }

    /** Parcelas de um hóspede (as abertas primeiro, na ordem em que o procedimento quita). */
    public List<Map<String, Object>> parcelasDoHospede(String cpf) throws SQLException {
        return JdbcUtils.consultar("""
                SELECT pa.id_pagamento, pa.numero_parcela, r.id_reserva, pa.data_vencimento, pa.valor,
                       pa.status, pg.status AS status_pagamento, DATE(pa.data_pagamento) AS data_pagamento
                FROM PARCELA pa
                INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
                INNER JOIN RESERVA r    ON r.id_reserva = pg.id_reserva
                WHERE r.cpf_hospede = ?
                  AND pa.status <> 'CANCELADA'
                ORDER BY pa.status = 'PAGA', pa.data_vencimento, pa.id_pagamento, pa.numero_parcela
                """, cpf);
    }

    /** Registros gravados pelo trigger trg_reserva_log_status. */
    public List<Map<String, Object>> logReservas() throws SQLException {
        return JdbcUtils.consultar("""
                SELECT l.id_log, l.id_reserva, p.nome AS hospede, l.status_anterior, l.status_novo,
                       DATE_FORMAT(l.data_hora, '%d/%m/%Y %H:%i:%s') AS data_hora, l.usuario
                FROM LOG_RESERVA l
                INNER JOIN RESERVA r ON r.id_reserva = l.id_reserva
                INNER JOIN PESSOA p  ON p.cpf = r.cpf_hospede
                ORDER BY l.id_log DESC
                LIMIT 50
                """);
    }

    private static Map<String, Object> primeiraLinha(CallableStatement chamada) throws SQLException {
        Map<String, Object> linha = new LinkedHashMap<>();
        if (chamada.execute()) {
            try (ResultSet resultado = chamada.getResultSet()) {
                ResultSetMetaData meta = resultado.getMetaData();
                if (resultado.next()) {
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        linha.put(meta.getColumnLabel(i), resultado.getObject(i));
                    }
                }
            }
        }
        return linha;
    }
}
