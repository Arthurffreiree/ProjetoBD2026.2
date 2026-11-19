package br.edu.cesar.hotel.web;

import br.edu.cesar.hotel.dao.ReservaDAO;
import br.edu.cesar.hotel.model.Reserva;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.edu.cesar.hotel.web.HttpUtils.*;

/**
 * Rotas da tabela RESERVA:
 *   GET    /api/reservas          lista
 *   GET    /api/reservas/opcoes   hóspedes para o formulário
 *   POST   /api/reservas          insere
 *   PUT    /api/reservas/{id}     altera
 *   DELETE /api/reservas/{id}     exclui
 *
 * Pela tela só se cria ou altera reserva PENDENTE, CONFIRMADA ou CANCELADA.
 * EM_HOSPEDAGEM e FINALIZADA mudam no check-in e no check-out (procedimento).
 */
public class ReservaHandler implements HttpHandler {

    private static final String CONTEXTO = "/api/reservas";
    private static final Set<String> STATUS_EDITAVEIS = Set.of("PENDENTE", "CONFIRMADA", "CANCELADA");

    private final ReservaDAO reservaDAO = new ReservaDAO();

    @Override
    public void handle(HttpExchange troca) {
        comTratamentoDeErros(troca, () -> {
            String metodo = troca.getRequestMethod();
            String id = sufixoDoCaminho(troca, CONTEXTO);

            if (id.isEmpty() && "GET".equals(metodo)) {
                listar(troca);
            } else if ("opcoes".equals(id) && "GET".equals(metodo)) {
                enviarJson(troca, 200, reservaDAO.opcoesHospedes());
            } else if (id.isEmpty() && "POST".equals(metodo)) {
                reservaDAO.inserir(lerReserva(lerFormulario(troca), 0));
                enviarJson(troca, 201, mensagem("Reserva cadastrada com sucesso."));
            } else if (!id.isEmpty() && "PUT".equals(metodo)) {
                alterar(troca, inteiroPositivo(id, "id"));
            } else if (!id.isEmpty() && "DELETE".equals(metodo)) {
                if (reservaDAO.excluir(inteiroPositivo(id, "id")) == 0) {
                    enviarErro(troca, 404, "Reserva não encontrada.");
                } else {
                    enviarJson(troca, 200, mensagem("Reserva " + id + " excluída com sucesso."));
                }
            } else {
                throw new MetodoNaoPermitido("Operação não suportada para reservas.");
            }
        });
    }

    private void listar(HttpExchange troca) throws IOException, SQLException {
        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Reserva reserva : reservaDAO.listar()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id_reserva", reserva.getIdReserva());
            item.put("cpf_hospede", reserva.getCpfHospede());
            item.put("hospede", reserva.getNomeHospede());
            item.put("numero_quarto", reserva.getNumeroQuarto());
            item.put("tipo", reserva.getNomeTipo());
            item.put("entrada_prevista", reserva.getEntradaPrevista());
            item.put("saida_prevista", reserva.getSaidaPrevista());
            item.put("qtd_hospedes", reserva.getQtdHospedes());
            item.put("status", reserva.getStatus());
            item.put("observacao", reserva.getObservacao());
            resposta.add(item);
        }
        enviarJson(troca, 200, resposta);
    }

    private void alterar(HttpExchange troca, int idReserva) throws IOException, SQLException {
        String atual = reservaDAO.statusAtual(idReserva);
        if (atual == null) {
            enviarErro(troca, 404, "Reserva não encontrada.");
            return;
        }
        if (!STATUS_EDITAVEIS.contains(atual)) {
            throw new IllegalArgumentException("Reserva " + atual + " não pode ser alterada pela tela (use o check-out).");
        }
        reservaDAO.atualizar(lerReserva(lerFormulario(troca), idReserva));
        enviarJson(troca, 200, mensagem("Reserva " + idReserva + " atualizada com sucesso."));
    }

    /**
     * Converte o formulário. Capacidade e conflito de datas são verificados
     * pelos triggers; o CHECK do banco garante saída posterior à entrada.
     */
    private Reserva lerReserva(Map<String, String> formulario, int idReserva) {
        String status = obrigatorio(formulario, "status");
        if (!STATUS_EDITAVEIS.contains(status)) {
            throw new IllegalArgumentException("Pela tela, a reserva só pode ficar PENDENTE, CONFIRMADA ou CANCELADA.");
        }
        String observacao = formulario.getOrDefault("observacao", "").trim();
        return new Reserva(
                idReserva,
                obrigatorio(formulario, "cpf_hospede"),
                obrigatorio(formulario, "numero_quarto"),
                data(obrigatorio(formulario, "entrada_prevista")),
                data(obrigatorio(formulario, "saida_prevista")),
                inteiroPositivo(obrigatorio(formulario, "qtd_hospedes"), "quantidade de hóspedes"),
                status,
                observacao.isEmpty() ? null : observacao);
    }

    private static LocalDate data(String texto) {
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException erro) {
            throw new IllegalArgumentException("Informe as datas no formato AAAA-MM-DD.");
        }
    }
}
