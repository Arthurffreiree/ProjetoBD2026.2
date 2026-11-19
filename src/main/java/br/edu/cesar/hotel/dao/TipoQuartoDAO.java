package br.edu.cesar.hotel.dao;

import br.edu.cesar.hotel.model.TipoQuarto;
import br.edu.cesar.hotel.util.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** CRUD da tabela TIPO_QUARTO com SQL explícito via PreparedStatement. */
public class TipoQuartoDAO {

    /** Lista os tipos com a quantidade de quartos de cada um (LEFT JOIN com QUARTO). */
    public List<TipoQuarto> listar() throws SQLException {
        String sql = """
                SELECT tq.id_tipo, tq.nome, tq.capacidade, tq.valor_diaria, tq.descricao,
                       COUNT(q.numero) AS quantidade_quartos
                FROM TIPO_QUARTO tq
                LEFT JOIN QUARTO q ON q.id_tipo = tq.id_tipo
                GROUP BY tq.id_tipo, tq.nome, tq.capacidade, tq.valor_diaria, tq.descricao
                ORDER BY tq.nome
                """;
        List<TipoQuarto> tipos = new ArrayList<>();
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {
            while (resultado.next()) {
                TipoQuarto tipo = new TipoQuarto(
                        resultado.getInt("id_tipo"),
                        resultado.getString("nome"),
                        resultado.getInt("capacidade"),
                        resultado.getBigDecimal("valor_diaria"),
                        resultado.getString("descricao"));
                tipo.setQuantidadeQuartos(resultado.getInt("quantidade_quartos"));
                tipos.add(tipo);
            }
        }
        return tipos;
    }

    public void inserir(TipoQuarto tipo) throws SQLException {
        String sql = "INSERT INTO TIPO_QUARTO (nome, capacidade, valor_diaria, descricao) VALUES (?, ?, ?, ?)";
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, tipo.getNome());
            comando.setInt(2, tipo.getCapacidade());
            comando.setBigDecimal(3, tipo.getValorDiaria());
            comando.setString(4, tipo.getDescricao());
            comando.executeUpdate();
        }
    }

    public int atualizar(TipoQuarto tipo) throws SQLException {
        String sql = "UPDATE TIPO_QUARTO SET nome = ?, capacidade = ?, valor_diaria = ?, descricao = ? WHERE id_tipo = ?";
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, tipo.getNome());
            comando.setInt(2, tipo.getCapacidade());
            comando.setBigDecimal(3, tipo.getValorDiaria());
            comando.setString(4, tipo.getDescricao());
            comando.setInt(5, tipo.getIdTipo());
            return comando.executeUpdate();
        }
    }

    /** Se existir quarto desse tipo, o MySQL recusa (FK de QUARTO) e lança SQLException. */
    public int excluir(int idTipo) throws SQLException {
        String sql = "DELETE FROM TIPO_QUARTO WHERE id_tipo = ?";
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, idTipo);
            return comando.executeUpdate();
        }
    }
}
