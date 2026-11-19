package br.edu.cesar.hotel;

import br.edu.cesar.hotel.util.ConnectionFactory;
import br.edu.cesar.hotel.web.ConsultaHandler;
import br.edu.cesar.hotel.web.DashboardHandler;
import br.edu.cesar.hotel.web.QuartoHandler;
import br.edu.cesar.hotel.web.ReservaHandler;
import br.edu.cesar.hotel.web.RotinaHandler;
import br.edu.cesar.hotel.web.ServicoHandler;
import br.edu.cesar.hotel.web.StaticHandler;
import br.edu.cesar.hotel.web.TipoQuartoHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Ponto de entrada do Sistema de Gerenciamento de Hotel (versão final, Etapa 06).
 *
 * Organização do código (mesmo padrão do projeto exemplo da disciplina):
 *   util  -> ConnectionFactory (conexão JDBC) e JdbcUtils
 *   model -> classes que representam as tabelas (Quarto, Servico, TipoQuarto, Reserva)
 *   dao   -> SQL explícito com PreparedStatement/CallableStatement (CRUD, consultas,
 *            visões, dashboard, funções, procedimentos e log dos triggers)
 *   web   -> servidor HTTP nativo do Java que liga a interface aos DAOs
 *
 * Não há ORM: todo INSERT, UPDATE, DELETE e SELECT é escrito em SQL e enviado
 * ao MySQL pelo JDBC.
 */
public final class HotelApplication {

    private HotelApplication() {
    }

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");

        int porta = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        servidor.createContext("/api/dashboard", new DashboardHandler());
        servidor.createContext("/api/tipos-quarto", new TipoQuartoHandler());
        servidor.createContext("/api/quartos", new QuartoHandler());
        servidor.createContext("/api/servicos", new ServicoHandler());
        servidor.createContext("/api/consultas", new ConsultaHandler());
        servidor.createContext("/api/reservas", new ReservaHandler());
        servidor.createContext("/api/rotinas", new RotinaHandler());
        servidor.createContext("/", new StaticHandler());
        servidor.setExecutor(Executors.newFixedThreadPool(12));
        servidor.start();

        System.out.println("Banco: " + ConnectionFactory.descricao());
        System.out.printf("Hotel Dashboard iniciado em http://localhost:%d%n", porta);
        System.out.println("Pressione Ctrl+C para encerrar.");
    }
}
