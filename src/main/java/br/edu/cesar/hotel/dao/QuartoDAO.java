package br.edu.cesar.hotel.dao;

import br.edu.cesar.hotel.model.Quarto;
import br.edu.cesar.hotel.util.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD da tabela QUARTO com SQL explícito (INSERT, SELECT, UPDATE e DELETE)
 * executado via PreparedStatement.
 */
public class QuartoDAO {

    /** Lista os quartos com os dados do tipo (INNER JOIN com TIPO_QUARTO). */
    public List<Quarto> listar() throws SQLException {
        String sql = """
                SELECT q.numero, q.andar, q.status, q.id_tipo,
                       tq.nome AS nome_tipo, tq.capacidade, tq.valor_diaria
                FROM QUARTO q
                INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
                ORDER BY CAST(q.numero AS UNSIGNED), q.numero
                """;
        List<Quarto> quartos = new ArrayList<>();
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {
            while (resultado.next()) {
                Quarto quarto = new Quarto(
                        resultado.getString("numero"),
                        resultado.getInt("andar"),
                        resultado.getString("status"),
                        resultado.getInt("id_tipo"));
                quarto.setNomeTipo(resultado.getString("nome_tipo"));
                quarto.setCapacidade(resultado.getInt("capacidade"));
                quarto.setValorDiaria(resultado.getBigDecimal("valor_diaria"));
                quartos.add(quarto);
            }
        }
        return quartos;
    }

    public void inserir(Quarto quarto) throws SQLException {
        String sql = "INSERT INTO QUARTO (numero, andar, status, id_tipo) VALUES (?, ?, ?, ?)";
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, quarto.getNumero());
            comando.setInt(2, quarto.getAndar());
            comando.setString(3, quarto.getStatus());
            comando.setInt(4, quarto.getIdTipo());
            comando.executeUpdate();
        }
    }

    /** Atualiza andar, status e tipo. Retorna a quantidade de linhas alteradas (0 = não encontrado). */
    public int atualizar(Quarto quarto) throws SQLException {
        String sql = "UPDATE QUARTO SET andar = ?, status = ?, id_tipo = ? WHERE numero = ?";
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, quarto.getAndar());
            comando.setString(2, quarto.getStatus());
            comando.setInt(3, quarto.getIdTipo());
            comando.setString(4, quarto.getNumero());
            return comando.executeUpdate();
        }
    }

    /** Exclui o quarto. Se houver reservas vinculadas, o MySQL recusa (FK) e lança SQLException. */
    public int excluir(String numero) throws SQLException {
        String sql = "DELETE FROM QUARTO WHERE numero = ?";
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, numero);
            return comando.executeUpdate();
        }
    }
}
