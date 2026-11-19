package br.edu.cesar.hotel.web;

import br.edu.cesar.hotel.dao.RotinaDAO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.util.Map;

import static br.edu.cesar.hotel.web.HttpUtils.*;

/**
 * Rotas da tela "Funções e triggers" (rotinas da Etapa 05):
 *   GET  /api/rotinas/categorias        fn_categoria_hospede
 *   GET  /api/rotinas/hospedagens       fn_valor_conta das hospedagens abertas
 *   POST /api/rotinas/checkout          CALL sp_realizar_checkout
 *   GET  /api/rotinas/devedores         hóspedes com parcela em aberto
 *   GET  /api/rotinas/parcelas?cpf=     parcelas de um hóspede
 *   POST /api/rotinas/pagamento         CALL sp_registrar_pagamento_hospede (cursor)
 *   GET  /api/rotinas/log               LOG_RESERVA (gravado por trigger)
 */
public class RotinaHandler implements HttpHandler {

    private static final String CONTEXTO = "/api/rotinas";

    private final RotinaDAO rotinaDAO = new RotinaDAO();

    @Override
    public void handle(HttpExchange troca) {
        comTratamentoDeErros(troca, () -> {
            String acao = sufixoDoCaminho(troca, CONTEXTO);
            String metodo = troca.getRequestMethod();

            switch (acao) {
                case "categorias" -> {
                    exigirMetodo(troca, "GET");
                    enviarJson(troca, 200, rotinaDAO.categoriasHospedes());
                }
                case "hospedagens" -> {
                    exigirMetodo(troca, "GET");
                    enviarJson(troca, 200, rotinaDAO.hospedagensAbertas());
                }
                case "checkout" -> {
                    exigirMetodo(troca, "POST");
                    int id = inteiroPositivo(obrigatorio(lerFormulario(troca), "id_hospedagem"), "hospedagem");
                    enviarJson(troca, 200, rotinaDAO.realizarCheckout(id));
                }
                case "devedores" -> {
                    exigirMetodo(troca, "GET");
                    enviarJson(troca, 200, rotinaDAO.devedores());
                }
                case "parcelas" -> {
                    exigirMetodo(troca, "GET");
                    enviarJson(troca, 200, rotinaDAO.parcelasDoHospede(obrigatorio(lerParametrosDaUrl(troca), "cpf")));
                }
                case "pagamento" -> {
                    exigirMetodo(troca, "POST");
                    Map<String, String> formulario = lerFormulario(troca);
                    enviarJson(troca, 200, rotinaDAO.registrarPagamento(
                            obrigatorio(formulario, "cpf"),
                            valorMonetario(obrigatorio(formulario, "valor"))));
                }
                case "log" -> {
                    exigirMetodo(troca, "GET");
                    enviarJson(troca, 200, rotinaDAO.logReservas());
                }
                default -> throw new MetodoNaoPermitido("Rotina não encontrada: " + acao + " (" + metodo + ").");
            }
        });
    }
}
