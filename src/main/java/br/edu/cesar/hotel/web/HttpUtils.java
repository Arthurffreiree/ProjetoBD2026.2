package br.edu.cesar.hotel.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Funções comuns aos handlers HTTP: leitura de formulários, validação de
 * campos, envio de respostas JSON e tradução de erros do MySQL em mensagens
 * compreensíveis para o usuário.
 */
public final class HttpUtils {

    private HttpUtils() {
    }

    /** Ação executada por um handler; pode lançar qualquer exceção. */
    @FunctionalInterface
    public interface Acao {
        void executar() throws Exception;
    }

    /** Erro para método HTTP não suportado (vira resposta 405). */
    public static final class MetodoNaoPermitido extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MetodoNaoPermitido(String mensagem) {
            super(mensagem);
        }
    }

    /** Executa a ação tratando os erros de forma centralizada. */
    public static void comTratamentoDeErros(HttpExchange troca, Acao acao) {
        try {
            acao.executar();
        } catch (MetodoNaoPermitido erro) {
            enviarErroSeguro(troca, 405, erro.getMessage());
        } catch (IllegalArgumentException erro) {
            enviarErroSeguro(troca, 400, erro.getMessage());
        } catch (SQLException erro) {
            System.err.println("[MySQL " + erro.getErrorCode() + "] " + erro.getMessage());
            // 23xxx = restrição (FK, UNIQUE, CHECK); 45000 = SIGNAL de trigger ou procedimento
            String estado = erro.getSQLState() == null ? "" : erro.getSQLState();
            boolean regraDoBanco = estado.startsWith("23") || estado.equals("45000") || erro.getErrorCode() == 3819;
            enviarErroSeguro(troca, regraDoBanco ? 409 : 500, mensagemAmigavel(erro));
        } catch (Exception erro) {
            erro.printStackTrace();
            enviarErroSeguro(troca, 500, "Erro interno: " + erro.getMessage());
        }
    }

    /** Traduz os códigos de erro mais comuns do MySQL. */
    static String mensagemAmigavel(SQLException erro) {
        return switch (erro.getErrorCode()) {
            case 1644 -> erro.getMessage().replaceFirst("^\\(conn=\\d+\\) ", ""); // texto do SIGNAL do trigger/procedimento
            case 1451 -> "Não é possível excluir: existem registros vinculados a este item (quartos, reservas, pagamentos ou consumos).";
            case 1452 -> "O valor informado não existe na tabela relacionada (ex.: quarto ou hóspede inexistente).";
            case 1062 -> "Já existe um registro com esse valor (número do quarto ou nome repetido).";
            case 3819 -> "Os dados violam uma regra de validação do banco (CHECK).";
            case 1406 -> "Um dos campos excede o tamanho máximo permitido.";
            case 1048 -> "Um campo obrigatório não foi informado.";
            default -> "Não foi possível executar a operação no MySQL: " + erro.getMessage();
        };
    }

    public static void exigirMetodo(HttpExchange troca, String metodo) {
        if (!metodo.equals(troca.getRequestMethod())) {
            throw new MetodoNaoPermitido("Método " + troca.getRequestMethod() + " não permitido.");
        }
    }

    /** Parte do caminho após o contexto. Ex.: /api/quartos/101 -> "101". */
    public static String sufixoDoCaminho(HttpExchange troca, String contexto) {
        String caminho = troca.getRequestURI().getPath();
        String sufixo = caminho.length() <= contexto.length() ? "" : caminho.substring(contexto.length());
        while (sufixo.startsWith("/")) {
            sufixo = sufixo.substring(1);
        }
        return URLDecoder.decode(sufixo, StandardCharsets.UTF_8);
    }

    /** Lê o corpo de um formulário application/x-www-form-urlencoded. */
    public static Map<String, String> lerFormulario(HttpExchange troca) throws IOException {
        String tipo = troca.getRequestHeaders().getFirst("Content-Type");
        if (tipo == null || !tipo.startsWith("application/x-www-form-urlencoded")) {
            throw new IllegalArgumentException("Envie os dados como application/x-www-form-urlencoded.");
        }
        String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return decodificar(corpo);
    }

    /** Lê os parâmetros da URL (?chave=valor). */
    public static Map<String, String> lerParametrosDaUrl(HttpExchange troca) {
        String consulta = troca.getRequestURI().getRawQuery();
        return decodificar(consulta == null ? "" : consulta);
    }

    private static Map<String, String> decodificar(String texto) {
        Map<String, String> valores = new LinkedHashMap<>();
        if (texto == null || texto.isBlank()) {
            return valores;
        }
        for (String par : texto.split("&")) {
            String[] partes = par.split("=", 2);
            String chave = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
            String valor = partes.length == 2 ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "";
            valores.put(chave, valor);
        }
        return valores;
    }

    public static String obrigatorio(Map<String, String> valores, String campo) {
        String valor = valores.get(campo);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo '" + campo + "' é obrigatório.");
        }
        return valor.trim();
    }

    public static int inteiroPositivo(String valor, String campo) {
        int numero = inteiro(valor, campo);
        if (numero <= 0) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser um inteiro positivo.");
        }
        return numero;
    }

    public static int inteiroNaoNegativo(String valor, String campo) {
        int numero = inteiro(valor, campo);
        if (numero < 0) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser zero ou positivo.");
        }
        return numero;
    }

    public static int inteiro(String valor, String campo) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser um número inteiro.");
        }
    }

    public static BigDecimal valorMonetario(String valor) {
        try {
            BigDecimal numero = new BigDecimal(valor.trim().replace(',', '.'));
            if (numero.signum() < 0) {
                throw new NumberFormatException();
            }
            return numero;
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("Informe um valor monetário válido (zero ou positivo).");
        }
    }

    public static boolean booleano(String valor) {
        return "true".equalsIgnoreCase(valor) || "1".equals(valor) || "on".equalsIgnoreCase(valor);
    }

    public static Map<String, Object> mensagem(String texto) {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", texto);
        return resposta;
    }

    public static void enviarJson(HttpExchange troca, int status, Object corpo) throws IOException {
        byte[] bytes = Json.de(corpo).getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        troca.getResponseHeaders().set("Cache-Control", "no-store");
        troca.sendResponseHeaders(status, bytes.length);
        troca.getResponseBody().write(bytes);
        troca.close();
    }

    public static void enviarErro(HttpExchange troca, int status, String mensagem) throws IOException {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("erro", mensagem);
        enviarJson(troca, status, corpo);
    }

    private static void enviarErroSeguro(HttpExchange troca, int status, String mensagem) {
        try {
            enviarErro(troca, status, mensagem);
        } catch (IOException ignorado) {
            troca.close();
        }
    }
}
