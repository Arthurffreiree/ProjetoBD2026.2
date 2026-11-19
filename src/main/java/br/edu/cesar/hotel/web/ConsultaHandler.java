package br.edu.cesar.hotel.web;

import br.edu.cesar.hotel.dao.ConsultaDAO;
import br.edu.cesar.hotel.dao.ConsultaDAO.Consulta;
import br.edu.cesar.hotel.dao.ConsultaDAO.Parametro;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.edu.cesar.hotel.web.HttpUtils.*;

/**
 * Rotas das consultas e visões:
 *   GET /api/consultas                lista (título, descrição, SQL, filtros e gráfico)
 *   GET /api/consultas/{id}?filtros   executa e devolve as linhas
 */
public class ConsultaHandler implements HttpHandler {

    private static final String CONTEXTO = "/api/consultas";

    private final ConsultaDAO consultaDAO = new ConsultaDAO();

    @Override
    public void handle(HttpExchange troca) {
        comTratamentoDeErros(troca, () -> {
            exigirMetodo(troca, "GET");
            String id = sufixoDoCaminho(troca, CONTEXTO);
            if (id.isEmpty()) {
                listar(troca);
                return;
            }
            Consulta consulta = consultaDAO.buscar(id);
            if (consulta == null) {
                enviarErro(troca, 404, "Consulta não encontrada.");
                return;
            }
            enviarJson(troca, 200, consultaDAO.executar(consulta, lerParametros(troca, consulta)));
        });
    }

    private void listar(HttpExchange troca) throws IOException {
        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Consulta consulta : consultaDAO.listar()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", consulta.id());
            item.put("grupo", consulta.grupo());
            item.put("titulo", consulta.titulo());
            item.put("descricao", consulta.descricao());
            item.put("conceitos", consulta.conceitos());
            item.put("sql", consulta.sql().strip());
            List<Map<String, Object>> parametros = new ArrayList<>();
            for (Parametro parametro : consulta.parametros()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("nome", parametro.nome());
                p.put("rotulo", parametro.rotulo());
                p.put("tipo", parametro.tipo());
                p.put("padrao", resolverPadrao(parametro));
                p.put("opcoes", parametro.opcoes());
                parametros.add(p);
            }
            item.put("parametros", parametros);
            Map<String, Object> grafico = new LinkedHashMap<>();
            grafico.put("tipo", consulta.grafico().tipo());
            grafico.put("rotulo", consulta.grafico().rotulo());
            grafico.put("valor", consulta.grafico().valor());
            item.put("grafico", grafico);
            resposta.add(item);
        }
        enviarJson(troca, 200, resposta);
    }

    /**
     * Lê e valida cada filtro da URL (ou usa o valor padrão) e monta a lista de
     * valores na ordem dos "?" do SQL.
     */
    private Object[] lerParametros(HttpExchange troca, Consulta consulta) {
        Map<String, String> url = lerParametrosDaUrl(troca);
        Map<String, Object> valores = new HashMap<>();
        for (Parametro parametro : consulta.parametros()) {
            String texto = url.getOrDefault(parametro.nome(), "").trim();
            if (texto.isEmpty()) {
                texto = resolverPadrao(parametro);
            }
            valores.put(parametro.nome(), converter(parametro, texto));
        }
        validarPeriodo(valores, "entrada", "saida", true);
        validarPeriodo(valores, "de", "ate", false);

        Object[] ordem = new Object[consulta.ordemParametros().size()];
        for (int i = 0; i < ordem.length; i++) {
            Object valor = valores.get(consulta.ordemParametros().get(i));
            ordem[i] = valor instanceof LocalDate data ? Date.valueOf(data) : valor;
        }
        return ordem;
    }

    private static Object converter(Parametro parametro, String texto) {
        switch (parametro.tipo()) {
            case "data":
                try {
                    return LocalDate.parse(texto);
                } catch (DateTimeParseException erro) {
                    throw new IllegalArgumentException("Informe " + parametro.rotulo() + " no formato AAAA-MM-DD.");
                }
            case "inteiro":
                return inteiroPositivo(texto, parametro.rotulo());
            case "opcao":
                if (!parametro.opcoes().contains(texto)) {
                    throw new IllegalArgumentException("Opção inválida para " + parametro.rotulo() + ".");
                }
                return texto;
            default:
                return texto;
        }
    }

    private static void validarPeriodo(Map<String, Object> valores, String inicio, String fim, boolean estrito) {
        if (valores.get(inicio) instanceof LocalDate a && valores.get(fim) instanceof LocalDate b) {
            if (estrito ? !b.isAfter(a) : b.isBefore(a)) {
                throw new IllegalArgumentException(estrito
                        ? "A data de saída deve ser posterior à de entrada."
                        : "A data final deve ser igual ou posterior à inicial.");
            }
        }
    }

    /** "hoje+N" vira a data de hoje mais N dias; os outros padrões ficam como estão. */
    private static String resolverPadrao(Parametro parametro) {
        String padrao = parametro.padrao();
        if (padrao != null && padrao.startsWith("hoje")) {
            int dias = padrao.length() > 4 ? Integer.parseInt(padrao.substring(4).replace("+", "")) : 0;
            return LocalDate.now().plusDays(dias).toString();
        }
        return padrao;
    }
}
