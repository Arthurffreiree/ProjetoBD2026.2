package br.edu.cesar.hotel.web;

import br.edu.cesar.hotel.dao.TipoQuartoDAO;
import br.edu.cesar.hotel.model.TipoQuarto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.edu.cesar.hotel.web.HttpUtils.*;

/**
 * Rotas da tabela TIPO_QUARTO:
 *   GET    /api/tipos-quarto       lista
 *   POST   /api/tipos-quarto       insere
 *   PUT    /api/tipos-quarto/{id}  altera
 *   DELETE /api/tipos-quarto/{id}  exclui
 */
public class TipoQuartoHandler implements HttpHandler {

    private static final String CONTEXTO = "/api/tipos-quarto";

    private final TipoQuartoDAO tipoQuartoDAO = new TipoQuartoDAO();

    @Override
    public void handle(HttpExchange troca) {
        comTratamentoDeErros(troca, () -> {
            String metodo = troca.getRequestMethod();
            String id = sufixoDoCaminho(troca, CONTEXTO);

            if (id.isEmpty() && "GET".equals(metodo)) {
                listar(troca);
            } else if (id.isEmpty() && "POST".equals(metodo)) {
                TipoQuarto tipo = lerTipo(lerFormulario(troca), 0);
                tipoQuartoDAO.inserir(tipo);
                enviarJson(troca, 201, mensagem("Tipo \"" + tipo.getNome() + "\" cadastrado com sucesso."));
            } else if (!id.isEmpty() && "PUT".equals(metodo)) {
                TipoQuarto tipo = lerTipo(lerFormulario(troca), inteiroPositivo(id, "id"));
                responder(troca, tipoQuartoDAO.atualizar(tipo), "Tipo \"" + tipo.getNome() + "\" atualizado com sucesso.");
            } else if (!id.isEmpty() && "DELETE".equals(metodo)) {
                responder(troca, tipoQuartoDAO.excluir(inteiroPositivo(id, "id")), "Tipo de quarto excluído com sucesso.");
            } else {
                throw new MetodoNaoPermitido("Operação não suportada para tipos de quarto.");
            }
        });
    }

    private void listar(HttpExchange troca) throws IOException, SQLException {
        List<Map<String, Object>> resposta = new ArrayList<>();
        for (TipoQuarto tipo : tipoQuartoDAO.listar()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id_tipo", tipo.getIdTipo());
            item.put("nome", tipo.getNome());
            item.put("capacidade", tipo.getCapacidade());
            item.put("valor_diaria", tipo.getValorDiaria());
            item.put("descricao", tipo.getDescricao());
            item.put("quantidade_quartos", tipo.getQuantidadeQuartos());
            resposta.add(item);
        }
        enviarJson(troca, 200, resposta);
    }

    private void responder(HttpExchange troca, int linhas, String texto) throws IOException {
        if (linhas == 0) {
            enviarErro(troca, 404, "Tipo de quarto não encontrado.");
        } else {
            enviarJson(troca, 200, mensagem(texto));
        }
    }

    /** As regras de valor (capacidade > 0, diária >= 0, nome único) ficam nos CHECK/UNIQUE do banco. */
    private TipoQuarto lerTipo(Map<String, String> formulario, int idTipo) {
        return new TipoQuarto(
                idTipo,
                obrigatorio(formulario, "nome"),
                inteiro(obrigatorio(formulario, "capacidade"), "capacidade"),
                valorMonetario(obrigatorio(formulario, "valor_diaria")),
                obrigatorio(formulario, "descricao"));
    }
}
