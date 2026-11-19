package br.edu.cesar.hotel.dao;

import br.edu.cesar.hotel.model.Reserva;
import br.edu.cesar.hotel.util.ConnectionFactory;
import br.edu.cesar.hotel.util.JdbcUtils;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CRUD da tabela RESERVA com SQL explícito.
 * As regras de capacidade e de overbooking são garantidas pelos triggers
 * trg_reserva_valida_insert e trg_reserva_valida_update (Etapa 05); quando
 * um trigger recusa, o MySQL lança SQLException com a mensagem do SIGNAL.
 * Toda mudança de status também é registrada em LOG_RESERVA por trigger.
 */
public class ReservaDAO {

    public List<Reserva> listar() throws SQLException {
        String sql = """
                SELECT r.id_reserva, r.cpf_hospede, r.numero_quarto, r.entrada_prevista,
                       r.saida_prevista, r.qtd_hospedes, r.status, r.observacao,
                       p.nome AS nome_hospede, tq.nome AS nome_tipo
                FROM RESERVA r
                INNER JOIN PESSOA p       ON p.cpf = r.cpf_hospede
                INNER JOIN QUARTO q       ON q.numero = r.numero_quarto
                INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
                ORDER BY r.entrada_prevista DESC, r.id_reserva DESC
                """;
        List<Reserva> reservas = new ArrayList<>();
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {
            while (resultado.next()) {
                Reserva reserva = new Reserva(
                        resultado.getInt("id_reserva"),
                        resultado.getString("cpf_hospede"),
                        resultado.getString("numero_quarto"),
                        resultado.getDate("entrada_prevista").toLocalDate(),
                        resultado.getDate("saida_prevista").toLocalDate(),
                        resultado.getInt("qtd_hospedes"),
                        resultado.getString("status"),
                        resultado.getString("observacao"));
                reserva.setNomeHospede(resultado.getString("nome_hospede"));
                reserva.setNomeTipo(resultado.getString("nome_tipo"));
                reservas.add(reserva);
            }
        }
        return reservas;
    }

    public void inserir(Reserva reserva) throws SQLException {
        String sql = """
                INSERT INTO RESERVA (cpf_hospede, numero_quarto, entrada_prevista, saida_prevista,
                                     qtd_hospedes, status, observacao)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            preencher(comando, reserva);
            comando.executeUpdate();
        }
    }

    public int atualizar(Reserva reserva) throws SQLException {
        String sql = """
                UPDATE RESERVA
                   SET cpf_hospede = ?, numero_quarto = ?, entrada_prevista = ?, saida_prevista = ?,
                       qtd_hospedes = ?, status = ?, observacao = ?
                 WHERE id_reserva = ?
                """;
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            preencher(comando, reserva);
            comando.setInt(8, reserva.getIdReserva());
            return comando.executeUpdate();
        }
    }

    /** Reserva com hospedagem ou pagamento vinculado não pode ser excluída (FK). */
    public int excluir(int idReserva) throws SQLException {
        String sql = "DELETE FROM RESERVA WHERE id_reserva = ?";
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, idReserva);
            return comando.executeUpdate();
        }
    }

    /** Status atual de uma reserva (null se não existir). */
    public String statusAtual(int idReserva) throws SQLException {
        Object status = JdbcUtils.valorUnico("SELECT status FROM RESERVA WHERE id_reserva = ?", idReserva);
        return status == null ? null : status.toString();
    }

    /** Hóspedes para o campo de seleção do formulário. */
    public List<Map<String, Object>> opcoesHospedes() throws SQLException {
        return JdbcUtils.consultar("""
                SELECT h.cpf, p.nome
                FROM HOSPEDE h
                INNER JOIN PESSOA p ON p.cpf = h.cpf
                ORDER BY p.nome
                """);
    }

    private void preencher(PreparedStatement comando, Reserva reserva) throws SQLException {
        comando.setString(1, reserva.getCpfHospede());
        comando.setString(2, reserva.getNumeroQuarto());
        comando.setDate(3, Date.valueOf(reserva.getEntradaPrevista()));
        comando.setDate(4, Date.valueOf(reserva.getSaidaPrevista()));
        comando.setInt(5, reserva.getQtdHospedes());
        comando.setString(6, reserva.getStatus());
        comando.setString(7, reserva.getObservacao());
    }
}
