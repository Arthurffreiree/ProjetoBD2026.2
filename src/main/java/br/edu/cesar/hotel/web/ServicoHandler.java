package br.edu.cesar.hotel.web;

import br.edu.cesar.hotel.dao.ServicoDAO;
import br.edu.cesar.hotel.model.Servico;
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
 * Rotas da tabela SERVICO:
 *   GET    /api/servicos      lista
 *   POST   /api/servicos      insere
 *   PUT    /api/servicos/{id} altera
 *   DELETE /api/servicos/{id} exclui
 */
public class ServicoHandler implements HttpHandler {

    private static final String CONTEXTO = "/api/servicos";

    private final ServicoDAO servicoDAO = new ServicoDAO();

    @Override
    public void handle(HttpExchange troca) {
        comTratamentoDeErros(troca, () -> {
            String metodo = troca.getRequestMethod();
            String id = sufixoDoCaminho(troca, CONTEXTO);

            if (id.isEmpty() && "GET".equals(metodo)) {
                listar(troca);
            } else if (id.isEmpty() && "POST".equals(metodo)) {
                inserir(troca);
            } else if (!id.isEmpty() && "PUT".equals(metodo)) {
                alterar(troca, inteiroPositivo(id, "id do serviço"));
            } else if (!id.isEmpty() && "DELETE".equals(metodo)) {
                excluir(troca, inteiroPositivo(id, "id do serviço"));
            } else {
                throw new MetodoNaoPermitido("Operação não suportada para serviços.");
            }
        });
    }

    private void listar(HttpExchange troca) throws IOException, SQLException {
        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Servico servico : servicoDAO.listar()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id_servico", servico.getIdServico());
            item.put("nome", servico.getNome());
            item.put("descricao", servico.getDescricao());
            item.put("valor_atual", servico.getValorAtual());
            item.put("ativo", servico.isAtivo());
            resposta.add(item);
        }
        enviarJson(troca, 200, resposta);
    }

    private void inserir(HttpExchange troca) throws IOException, SQLException {
        Servico servico = lerServico(lerFormulario(troca), 0);
        servicoDAO.inserir(servico);
        enviarJson(troca, 201, mensagem("Serviço \"" + servico.getNome() + "\" cadastrado com sucesso."));
    }

    private void alterar(HttpExchange troca, int id) throws IOException, SQLException {
        Servico servico = lerServico(lerFormulario(troca), id);
        if (servicoDAO.atualizar(servico) == 0) {
            enviarErro(troca, 404, "Serviço não encontrado.");
            return;
        }
        enviarJson(troca, 200, mensagem("Serviço \"" + servico.getNome() + "\" atualizado com sucesso."));
    }

    private void excluir(HttpExchange troca, int id) throws IOException, SQLException {
        if (servicoDAO.excluir(id) == 0) {
            enviarErro(troca, 404, "Serviço não encontrado.");
            return;
        }
        enviarJson(troca, 200, mensagem("Serviço excluído com sucesso."));
    }

    /** Valida os campos do formulário e monta o objeto Servico. */
    private Servico lerServico(Map<String, String> formulario, int id) {
        String nome = obrigatorio(formulario, "nome");
        String descricao = obrigatorio(formulario, "descricao");
        if (nome.length() > 80 || descricao.length() > 255) {
            throw new IllegalArgumentException("Nome (até 80) ou descrição (até 255) acima do tamanho permitido.");
        }
        return new Servico(id, nome, descricao,
                valorMonetario(obrigatorio(formulario, "valor_atual")),
                booleano(formulario.get("ativo")));
    }
}
