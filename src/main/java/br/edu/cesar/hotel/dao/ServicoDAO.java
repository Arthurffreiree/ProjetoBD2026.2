package br.edu.cesar.hotel.dao;

import br.edu.cesar.hotel.model.Servico;
import br.edu.cesar.hotel.util.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD da tabela SERVICO com SQL explícito (INSERT, SELECT, UPDATE e DELETE)
 * executado via PreparedStatement.
 */
public class ServicoDAO {

    public List<Servico> listar() throws SQLException {
        String sql = "SELECT id_servico, nome, descricao, valor_atual, ativo FROM SERVICO ORDER BY nome";
        List<Servico> servicos = new ArrayList<>();
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {
            while (resultado.next()) {
                servicos.add(new Servico(
                        resultado.getInt("id_servico"),
                        resultado.getString("nome"),
                        resultado.getString("descricao"),
                        resultado.getBigDecimal("valor_atual"),
                        resultado.getBoolean("ativo")));
            }
        }
        return servicos;
    }

    public void inserir(Servico servico) throws SQLException {
        String sql = "INSERT INTO SERVICO (nome, descricao, valor_atual, ativo) VALUES (?, ?, ?, ?)";
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, servico.getNome());
            comando.setString(2, servico.getDescricao());
            comando.setBigDecimal(3, servico.getValorAtual());
            comando.setBoolean(4, servico.isAtivo());
            comando.executeUpdate();
        }
    }

    /** Retorna a quantidade de linhas alteradas (0 = serviço não encontrado). */
    public int atualizar(Servico servico) throws SQLException {
        String sql = "UPDATE SERVICO SET nome = ?, descricao = ?, valor_atual = ?, ativo = ? WHERE id_servico = ?";
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, servico.getNome());
            comando.setString(2, servico.getDescricao());
            comando.setBigDecimal(3, servico.getValorAtual());
            comando.setBoolean(4, servico.isAtivo());
            comando.setInt(5, servico.getIdServico());
            return comando.executeUpdate();
        }
    }

    /** Exclui o serviço. Se houver consumos vinculados, o MySQL recusa (FK) e lança SQLException. */
    public int excluir(int idServico) throws SQLException {
        String sql = "DELETE FROM SERVICO WHERE id_servico = ?";
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, idServico);
            return comando.executeUpdate();
        }
    }
}
