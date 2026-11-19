package br.edu.cesar.hotel.web;

import br.edu.cesar.hotel.dao.DashboardDAO;
import br.edu.cesar.hotel.dao.DashboardDAO.Filtro;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import static br.edu.cesar.hotel.web.HttpUtils.*;

/**
 * GET /api/dashboard?de=AAAA-MM-DD&ate=AAAA-MM-DD&tipo=ID
 * Indicadores, estatística descritiva e os dados dos 8 gráficos, já filtrados.
 * Sem filtro: período de 01/01/2026 a 31/12/2027 e todos os tipos.
 */
public class DashboardHandler implements HttpHandler {

    private final DashboardDAO dashboardDAO = new DashboardDAO();

    @Override
    public void handle(HttpExchange troca) {
        comTratamentoDeErros(troca, () -> {
            exigirMetodo(troca, "GET");
            Filtro filtro = lerFiltro(lerParametrosDaUrl(troca));

            Map<String, Object> indicadores = new LinkedHashMap<>();
            indicadores.put("hospedes", dashboardDAO.totalHospedes());
            indicadores.put("receitaRecebida", dashboardDAO.receitaRecebida(filtro));
            indicadores.putAll(dashboardDAO.resumoReservas(filtro));
            indicadores.putAll(dashboardDAO.ocupacaoAtual(filtro));

            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("indicadores", indicadores);
            resposta.put("estatisticas", dashboardDAO.estatisticas(filtro));
            resposta.put("correlacoes", dashboardDAO.correlacoes(filtro));
            resposta.put("reservasPorStatus", dashboardDAO.reservasPorStatus(filtro));
            resposta.put("receitaMensal", dashboardDAO.receitaMensal(filtro));
            resposta.put("reservasMensais", dashboardDAO.reservasMensais(filtro));
            resposta.put("distribuicaoValores", dashboardDAO.distribuicaoValores(filtro));
            resposta.put("reservasPorTipo", dashboardDAO.reservasPorTipo(filtro));
            resposta.put("dispersao", dashboardDAO.dispersaoNoitesValor(filtro));
            resposta.put("servicos", dashboardDAO.servicosMaisConsumidos(filtro));
            resposta.put("diasDaSemana", dashboardDAO.entradasPorDiaDaSemana(filtro));
            resposta.put("formasPagamento", dashboardDAO.formasDePagamento(filtro));
            resposta.put("ultimasReservas", dashboardDAO.ultimasReservas(filtro));
            enviarJson(troca, 200, resposta);
        });
    }

    private Filtro lerFiltro(Map<String, String> url) {
        try {
            LocalDate de = LocalDate.parse(url.getOrDefault("de", "2026-01-01"));
            LocalDate ate = LocalDate.parse(url.getOrDefault("ate", "2027-12-31"));
            if (ate.isBefore(de)) {
                throw new IllegalArgumentException("A data final deve ser igual ou posterior à inicial.");
            }
            String tipo = url.getOrDefault("tipo", "0");
            return new Filtro(de, ate, tipo.isBlank() ? 0 : Integer.parseInt(tipo));
        } catch (DateTimeParseException | NumberFormatException erro) {
            throw new IllegalArgumentException("Filtro inválido: use datas AAAA-MM-DD e um tipo de quarto válido.");
        }
    }
}
