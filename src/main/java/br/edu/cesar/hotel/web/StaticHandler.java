package br.edu.cesar.hotel.web;

import br.edu.cesar.hotel.util.JdbcUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static br.edu.cesar.hotel.web.HttpUtils.*;

/**
 * Serve os arquivos da interface (HTML, CSS e JS em src/main/resources/static)
 * e responde /api/health, usado pelo indicador "MySQL conectado".
 */
public class StaticHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange troca) throws IOException {
        String caminho = troca.getRequestURI().getPath();
        if ("/api/health".equals(caminho)) {
            saude(troca);
            return;
        }
        if (!"GET".equals(troca.getRequestMethod())) {
            enviarErro(troca, 405, "Método não permitido.");
            return;
        }
        if ("/".equals(caminho)) {
            caminho = "/index.html";
        }
        if (caminho.contains("..")) {
            enviarErro(troca, 400, "Caminho inválido.");
            return;
        }
        try (InputStream arquivo = StaticHandler.class.getResourceAsStream("/static" + caminho)) {
            if (arquivo == null) {
                enviarErro(troca, 404, "Arquivo não encontrado.");
                return;
            }
            byte[] bytes = arquivo.readAllBytes();
            troca.getResponseHeaders().set("Content-Type", tipoDeConteudo(caminho));
            troca.getResponseHeaders().set("Cache-Control", "no-cache");
            troca.sendResponseHeaders(200, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        }
    }

    private void saude(HttpExchange troca) {
        comTratamentoDeErros(troca, () -> {
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("status", "online");
            resposta.put("banco", JdbcUtils.valorUnico("SELECT DATABASE()"));
            resposta.put("versao", JdbcUtils.valorUnico("SELECT VERSION()"));
            enviarJson(troca, 200, resposta);
        });
    }

    private static String tipoDeConteudo(String caminho) {
        if (caminho.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (caminho.endsWith(".js")) {
            return "text/javascript; charset=UTF-8";
        }
        if (caminho.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "text/html; charset=UTF-8";
    }
}
