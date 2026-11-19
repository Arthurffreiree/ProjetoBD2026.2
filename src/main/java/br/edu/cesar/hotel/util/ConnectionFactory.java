package br.edu.cesar.hotel.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Fábrica de conexões JDBC com o MySQL (mesmo papel da ConnectionFactory do
 * projeto exemplo da disciplina).
 *
 * As configurações são lidas nesta ordem de prioridade:
 * 1. variáveis de ambiente DB_URL, DB_USER e DB_PASSWORD;
 * 2. arquivo db.properties na pasta onde a aplicação é executada
 *    (chaves db.url, db.user e db.password);
 * 3. valores padrão definidos abaixo.
 */
public final class ConnectionFactory {

    private static final String URL_PADRAO =
            "jdbc:mysql://localhost:3306/hotel_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Recife";

    // O arquivo precisa ser carregado antes das constantes que dependem dele.
    private static final Properties ARQUIVO = carregarArquivo();
    private static final String URL = configuracao("DB_URL", "db.url", URL_PADRAO);
    private static final String USUARIO = configuracao("DB_USER", "db.user", "root");
    private static final String SENHA = configuracao("DB_PASSWORD", "db.password", "");

    private ConnectionFactory() {
    }

    /** Abre uma nova conexão com o banco hotel_db. Quem chama deve fechá-la (try-with-resources). */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, SENHA);
    }

    /** Texto usado no log de inicialização (sem a senha). */
    public static String descricao() {
        return URL + " (usuário " + USUARIO + ")";
    }

    private static Properties carregarArquivo() {
        Properties propriedades = new Properties();
        Path arquivo = Path.of("db.properties");
        if (Files.exists(arquivo)) {
            try (Reader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
                propriedades.load(leitor);
            } catch (IOException erro) {
                System.err.println("Não foi possível ler db.properties: " + erro.getMessage());
            }
        }
        return propriedades;
    }

    private static String configuracao(String variavelAmbiente, String chaveArquivo, String valorPadrao) {
        String valor = System.getenv(variavelAmbiente);
        if (valor == null || valor.isBlank()) {
            valor = ARQUIVO.getProperty(chaveArquivo);
        }
        return valor == null || valor.isBlank() ? valorPadrao : valor.trim();
    }
}
