package br.edu.cesar.hotel.web;

import br.edu.cesar.hotel.dao.QuartoDAO;
import br.edu.cesar.hotel.model.Quarto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.edu.cesar.hotel.web.HttpUtils.*;

/**
 * Rotas da tabela QUARTO:
 *   GET    /api/quartos          lista
 *   POST   /api/quartos          insere
 *   PUT    /api/quartos/{numero} altera
 *   DELETE /api/quartos/{numero} exclui
 */
public class QuartoHandler implements HttpHandler {

    private static final String CONTEXTO = "/api/quartos";
    private static final Set<String> STATUS_VALIDOS = Set.of("DISPONIVEL", "OCUPADO", "LIMPEZA", "MANUTENCAO");

    private final QuartoDAO quartoDAO = new QuartoDAO();

    @Override
    public void handle(HttpExchange troca) {
        comTratamentoDeErros(troca, () -> {
            String metodo = troca.getRequestMethod();
            String numero = sufixoDoCaminho(troca, CONTEXTO);

            if (numero.isEmpty() && "GET".equals(metodo)) {
                listar(troca);
            } else if (numero.isEmpty() && "POST".equals(metodo)) {
                inserir(troca);
            } else if (!numero.isEmpty() && "PUT".equals(metodo)) {
                alterar(troca, numero);
            } else if (!numero.isEmpty() && "DELETE".equals(metodo)) {
                excluir(troca, numero);
            } else {
                throw new MetodoNaoPermitido("Operação não suportada para quartos.");
            }
        });
    }

    private void listar(HttpExchange troca) throws IOException, SQLException {
        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Quarto quarto : quartoDAO.listar()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("numero", quarto.getNumero());
            item.put("andar", quarto.getAndar());
            item.put("status", quarto.getStatus());
            item.put("id_tipo", quarto.getIdTipo());
            item.put("tipo", quarto.getNomeTipo());
            item.put("capacidade", quarto.getCapacidade());
            item.put("valor_diaria", quarto.getValorDiaria());
            resposta.add(item);
        }
        enviarJson(troca, 200, resposta);
    }

    private void inserir(HttpExchange troca) throws IOException, SQLException {
        Map<String, String> formulario = lerFormulario(troca);
        Quarto quarto = lerQuarto(formulario, obrigatorio(formulario, "numero"));
        quartoDAO.inserir(quarto);
        enviarJson(troca, 201, mensagem("Quarto " + quarto.getNumero() + " cadastrado com sucesso."));
    }

    private void alterar(HttpExchange troca, String numero) throws IOException, SQLException {
        Quarto quarto = lerQuarto(lerFormulario(troca), numero);
        if (quartoDAO.atualizar(quarto) == 0) {
            enviarErro(troca, 404, "Quarto não encontrado.");
            return;
        }
        enviarJson(troca, 200, mensagem("Quarto " + numero + " atualizado com sucesso."));
    }

    private void excluir(HttpExchange troca, String numero) throws IOException, SQLException {
        if (quartoDAO.excluir(numero) == 0) {
            enviarErro(troca, 404, "Quarto não encontrado.");
            return;
        }
        enviarJson(troca, 200, mensagem("Quarto " + numero + " excluído com sucesso."));
    }

    /** Valida os campos do formulário e monta o objeto Quarto. */
    private Quarto lerQuarto(Map<String, String> formulario, String numero) {
        if (numero.length() > 10) {
            throw new IllegalArgumentException("O número do quarto deve ter no máximo 10 caracteres.");
        }
        int andar = inteiroNaoNegativo(obrigatorio(formulario, "andar"), "andar");
        String status = obrigatorio(formulario, "status");
        if (!STATUS_VALIDOS.contains(status)) {
            throw new IllegalArgumentException("Status de quarto inválido.");
        }
        int idTipo = inteiroPositivo(obrigatorio(formulario, "id_tipo"), "tipo de quarto");
        return new Quarto(numero, andar, status, idTipo);
    }
}
