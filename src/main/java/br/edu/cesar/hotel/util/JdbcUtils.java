package br.edu.cesar.hotel.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Funções auxiliares de JDBC usadas pelos DAOs.
 *
 * Todo SQL continua explícito nos DAOs; esta classe apenas preenche os
 * parâmetros "?" do PreparedStatement e converte o ResultSet em linhas
 * genéricas (coluna -> valor) para exibição na interface.
 */
public final class JdbcUtils {

    private JdbcUtils() {
    }

    /** Cria o PreparedStatement e preenche os parâmetros na ordem recebida. */
    public static PreparedStatement preparar(Connection conexao, String sql, Object... parametros) throws SQLException {
        PreparedStatement comando = conexao.prepareStatement(sql);
        for (int indice = 0; indice < parametros.length; indice++) {
            comando.setObject(indice + 1, parametros[indice]);
        }
        return comando;
    }

    /** Executa um SELECT e devolve cada linha como um mapa ordenado (nome da coluna -> valor). */
    public static List<Map<String, Object>> consultar(String sql, Object... parametros) throws SQLException {
        List<Map<String, Object>> linhas = new ArrayList<>();
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = preparar(conexao, sql, parametros);
             ResultSet resultado = comando.executeQuery()) {
            ResultSetMetaData metadados = resultado.getMetaData();
            while (resultado.next()) {
                Map<String, Object> linha = new LinkedHashMap<>();
                for (int coluna = 1; coluna <= metadados.getColumnCount(); coluna++) {
                    linha.put(metadados.getColumnLabel(coluna), resultado.getObject(coluna));
                }
                linhas.add(linha);
            }
        }
        return linhas;
    }

    /** Executa um SELECT que retorna um único valor (ex.: COUNT ou SUM). */
    public static Object valorUnico(String sql, Object... parametros) throws SQLException {
        try (Connection conexao = ConnectionFactory.getConnection();
             PreparedStatement comando = preparar(conexao, sql, parametros);
             ResultSet resultado = comando.executeQuery()) {
            return resultado.next() ? resultado.getObject(1) : null;
        }
    }
}
