package br.edu.cesar.hotel.dao;

import br.edu.cesar.hotel.util.JdbcUtils;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Consultas do dashboard estatístico. Tudo é calculado no MySQL a partir dos
 * dados do banco; a interface só formata e desenha.
 *
 * Todos os métodos recebem o mesmo filtro escolhido na tela:
 *   período  -> data de entrada da reserva entre "de" e "ate"
 *   tipo     -> id do tipo de quarto (0 = todos)
 */
public class DashboardDAO {

    /** Filtro da tela. Os 4 parâmetros entram na mesma ordem dos "?" de FILTRO_RESERVA. */
    public record Filtro(LocalDate de, LocalDate ate, int idTipo) {
        Object[] parametros() {
            return new Object[]{Date.valueOf(de), Date.valueOf(ate), idTipo, idTipo};
        }
    }

    /** Condição usada em todas as consultas sobre reservas (alias r = RESERVA, q = QUARTO). */
    private static final String FILTRO_RESERVA =
            " r.entrada_prevista BETWEEN ? AND ? AND (? = 0 OR q.id_tipo = ?) ";

    /** Reservas não canceladas do filtro, com valor, noites e hóspedes (base da estatística). */
    private static final String CTE_VALORES = """
            WITH valores AS (
                SELECT pg.valor_total AS valor,
                       DATEDIFF(r.saida_prevista, r.entrada_prevista) AS noites,
                       r.qtd_hospedes AS hospedes
                FROM RESERVA r
                INNER JOIN QUARTO q     ON q.numero = r.numero_quarto
                INNER JOIN PAGAMENTO pg ON pg.id_reserva = r.id_reserva
                WHERE r.status <> 'CANCELADA' AND""" + FILTRO_RESERVA + ")\n";

    // ------------------------------------------------------------ indicadores

    public long totalHospedes() throws SQLException {
        return numero(JdbcUtils.valorUnico("SELECT COUNT(*) FROM HOSPEDE"));
    }

    /** Totais do período: reservas, canceladas, ticket médio, noites médias e hóspedes por reserva. */
    public Map<String, Object> resumoReservas(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar("""
                SELECT COUNT(*) AS reservas,
                       SUM(r.status = 'CANCELADA') AS canceladas,
                       ROUND(100 * SUM(r.status = 'CANCELADA') / NULLIF(COUNT(*), 0), 1) AS taxa_cancelamento,
                       ROUND(AVG(CASE WHEN r.status <> 'CANCELADA' THEN pg.valor_total END), 2) AS ticket_medio,
                       ROUND(AVG(DATEDIFF(r.saida_prevista, r.entrada_prevista)), 1) AS noites_media,
                       ROUND(AVG(r.qtd_hospedes), 1) AS hospedes_media
                FROM RESERVA r
                INNER JOIN QUARTO q ON q.numero = r.numero_quarto
                LEFT JOIN PAGAMENTO pg ON pg.id_reserva = r.id_reserva
                WHERE""" + FILTRO_RESERVA, filtro.parametros()).get(0);
    }

    /** Receita recebida: parcelas pagas com data de pagamento dentro do período. */
    public Object receitaRecebida(Filtro filtro) throws SQLException {
        return JdbcUtils.valorUnico("""
                SELECT COALESCE(SUM(pa.valor), 0)
                FROM PARCELA pa
                INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
                INNER JOIN RESERVA r    ON r.id_reserva = pg.id_reserva
                INNER JOIN QUARTO q     ON q.numero = r.numero_quarto
                WHERE pa.status = 'PAGA'
                  AND DATE(pa.data_pagamento) BETWEEN ? AND ?
                  AND (? = 0 OR q.id_tipo = ?)
                """, filtro.parametros());
    }

    /** Situação atual dos quartos (não depende do período). */
    public Map<String, Object> ocupacaoAtual(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar("""
                SELECT COUNT(*) AS quartos,
                       SUM(q.status = 'OCUPADO') AS ocupados,
                       SUM(q.status = 'DISPONIVEL') AS disponiveis,
                       ROUND(100 * SUM(q.status = 'OCUPADO') / NULLIF(COUNT(*), 0), 1) AS taxa_ocupacao
                FROM QUARTO q
                WHERE (? = 0 OR q.id_tipo = ?)
                """, filtro.idTipo(), filtro.idTipo()).get(0);
    }

    // ------------------------------------------------------------ gráficos

    /** Gráfico 1 (rosca): reservas por status. */
    public List<Map<String, Object>> reservasPorStatus(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar("""
                SELECT r.status AS label, COUNT(*) AS valor
                FROM RESERVA r
                INNER JOIN QUARTO q ON q.numero = r.numero_quarto
                WHERE""" + FILTRO_RESERVA + """
                GROUP BY r.status
                ORDER BY valor DESC
                """, filtro.parametros());
    }

    /** Gráfico 2 (linha): receita recebida por mês (data do pagamento). */
    public List<Map<String, Object>> receitaMensal(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar("""
                SELECT DATE_FORMAT(pa.data_pagamento, '%Y-%m') AS label, SUM(pa.valor) AS valor
                FROM PARCELA pa
                INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
                INNER JOIN RESERVA r    ON r.id_reserva = pg.id_reserva
                INNER JOIN QUARTO q     ON q.numero = r.numero_quarto
                WHERE pa.status = 'PAGA'
                  AND DATE(pa.data_pagamento) BETWEEN ? AND ?
                  AND (? = 0 OR q.id_tipo = ?)
                GROUP BY DATE_FORMAT(pa.data_pagamento, '%Y-%m')
                ORDER BY label
                """, filtro.parametros());
    }

    /** Gráfico 2 (barras do mesmo gráfico): reservas por mês de entrada. */
    public List<Map<String, Object>> reservasMensais(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar("""
                SELECT DATE_FORMAT(r.entrada_prevista, '%Y-%m') AS label, COUNT(*) AS valor
                FROM RESERVA r
                INNER JOIN QUARTO q ON q.numero = r.numero_quarto
                WHERE r.status <> 'CANCELADA' AND""" + FILTRO_RESERVA + """
                GROUP BY DATE_FORMAT(r.entrada_prevista, '%Y-%m')
                ORDER BY label
                """, filtro.parametros());
    }

    /** Gráfico 3 (histograma): reservas por faixa de valor de R$ 1.000. */
    public List<Map<String, Object>> distribuicaoValores(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar(CTE_VALORES + """
                SELECT CONCAT('R$ ', faixa, ' a ', faixa + 1, ' mil') AS label, COUNT(*) AS valor
                FROM (SELECT FLOOR(valor / 1000) AS faixa FROM valores) faixas
                GROUP BY faixa
                ORDER BY faixa
                """, filtro.parametros());
    }

    /**
     * Estatística descritiva do valor das reservas: n, média, mediana, moda,
     * variância, desvio padrão, coeficiente de variação, mínimo e máximo.
     * Também a média e a moda do número de noites.
     */
    public Map<String, Object> estatisticas(Filtro filtro) throws SQLException {
        Object[] p = filtro.parametros();
        Map<String, Object> resumo = JdbcUtils.consultar(CTE_VALORES + """
                SELECT COUNT(*) AS n,
                       ROUND(AVG(valor), 2) AS media,
                       ROUND(VAR_SAMP(valor), 2) AS variancia,
                       ROUND(STDDEV_SAMP(valor), 2) AS desvio_padrao,
                       ROUND(100 * STDDEV_SAMP(valor) / NULLIF(AVG(valor), 0), 1) AS coef_variacao,
                       MIN(valor) AS minimo,
                       MAX(valor) AS maximo,
                       ROUND(AVG(noites), 1) AS noites_media
                FROM valores
                """, p).get(0);
        resumo.put("mediana", JdbcUtils.valorUnico(CTE_VALORES + """
                SELECT ROUND(AVG(valor), 2)
                FROM (SELECT valor,
                             ROW_NUMBER() OVER (ORDER BY valor) AS posicao,
                             COUNT(*) OVER () AS total
                      FROM valores) ordenados
                WHERE posicao IN (FLOOR((total + 1) / 2), CEIL((total + 1) / 2))
                """, p));
        List<Map<String, Object>> moda = JdbcUtils.consultar(CTE_VALORES + """
                SELECT valor, COUNT(*) AS frequencia
                FROM valores
                GROUP BY valor
                ORDER BY frequencia DESC, valor
                LIMIT 1
                """, p);
        resumo.put("moda", moda.isEmpty() ? null : moda.get(0).get("valor"));
        resumo.put("moda_frequencia", moda.isEmpty() ? 0 : moda.get(0).get("frequencia"));
        List<Map<String, Object>> modaNoites = JdbcUtils.consultar(CTE_VALORES + """
                SELECT noites, COUNT(*) AS frequencia
                FROM valores
                GROUP BY noites
                ORDER BY frequencia DESC, noites
                LIMIT 1
                """, p);
        resumo.put("noites_moda", modaNoites.isEmpty() ? null : modaNoites.get(0).get("noites"));
        return resumo;
    }

    /** Gráfico 4 (barras): reservas e receita prevista por tipo de quarto (10 maiores). */
    public List<Map<String, Object>> reservasPorTipo(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar("""
                SELECT tq.nome AS label, COUNT(*) AS valor, SUM(pg.valor_total) AS receita
                FROM RESERVA r
                INNER JOIN QUARTO q       ON q.numero = r.numero_quarto
                INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
                INNER JOIN PAGAMENTO pg   ON pg.id_reserva = r.id_reserva
                WHERE r.status <> 'CANCELADA' AND""" + FILTRO_RESERVA + """
                GROUP BY tq.nome
                ORDER BY valor DESC, receita DESC
                LIMIT 10
                """, filtro.parametros());
    }

    /** Gráfico 5 (dispersão): noites x valor de cada reserva. */
    public List<Map<String, Object>> dispersaoNoitesValor(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar(CTE_VALORES + "SELECT noites AS x, valor AS y FROM valores", filtro.parametros());
    }

    /** Coeficiente de correlação de Pearson (r) calculado em SQL. */
    public Map<String, Object> correlacoes(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar(CTE_VALORES + """
                SELECT ROUND((COUNT(*) * SUM(noites * valor) - SUM(noites) * SUM(valor))
                       / NULLIF(SQRT((COUNT(*) * SUM(noites * noites) - SUM(noites) * SUM(noites))
                                   * (COUNT(*) * SUM(valor * valor) - SUM(valor) * SUM(valor))), 0), 3) AS r_noites_valor,
                       ROUND((COUNT(*) * SUM(hospedes * valor) - SUM(hospedes) * SUM(valor))
                       / NULLIF(SQRT((COUNT(*) * SUM(hospedes * hospedes) - SUM(hospedes) * SUM(hospedes))
                                   * (COUNT(*) * SUM(valor * valor) - SUM(valor) * SUM(valor))), 0), 3) AS r_hospedes_valor
                FROM valores
                """, filtro.parametros()).get(0);
    }

    /** Gráfico 6 (barras): serviços com maior receita (consumos dentro do período). */
    public List<Map<String, Object>> servicosMaisConsumidos(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar("""
                SELECT s.nome AS label, SUM(c.quantidade * c.valor_unitario) AS valor, SUM(c.quantidade) AS quantidade
                FROM CONSUMO_SERVICO c
                INNER JOIN SERVICO s    ON s.id_servico = c.id_servico
                INNER JOIN HOSPEDAGEM h ON h.id_hospedagem = c.id_hospedagem
                INNER JOIN RESERVA r    ON r.id_reserva = h.id_reserva
                INNER JOIN QUARTO q     ON q.numero = r.numero_quarto
                WHERE DATE(c.data_hora) BETWEEN ? AND ?
                  AND (? = 0 OR q.id_tipo = ?)
                GROUP BY s.nome
                ORDER BY valor DESC
                LIMIT 8
                """, filtro.parametros());
    }

    /** Gráfico 7 (radar): entradas por dia da semana (1 = domingo ... 7 = sábado). */
    public List<Map<String, Object>> entradasPorDiaDaSemana(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar("""
                SELECT DAYOFWEEK(r.entrada_prevista) AS dia, COUNT(*) AS valor
                FROM RESERVA r
                INNER JOIN QUARTO q ON q.numero = r.numero_quarto
                WHERE r.status <> 'CANCELADA' AND""" + FILTRO_RESERVA + """
                GROUP BY DAYOFWEEK(r.entrada_prevista)
                ORDER BY dia
                """, filtro.parametros());
    }

    /** Gráfico 8 (pizza): valor das reservas por forma de pagamento. */
    public List<Map<String, Object>> formasDePagamento(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar("""
                SELECT pg.forma_pagamento AS label, SUM(pg.valor_total) AS valor, COUNT(*) AS quantidade
                FROM PAGAMENTO pg
                INNER JOIN RESERVA r ON r.id_reserva = pg.id_reserva
                INNER JOIN QUARTO q  ON q.numero = r.numero_quarto
                WHERE r.status <> 'CANCELADA' AND""" + FILTRO_RESERVA + """
                GROUP BY pg.forma_pagamento
                ORDER BY valor DESC
                """, filtro.parametros());
    }

    /** Tabela: últimas reservas do filtro. */
    public List<Map<String, Object>> ultimasReservas(Filtro filtro) throws SQLException {
        return JdbcUtils.consultar("""
                SELECT r.id_reserva, p.nome AS hospede, r.numero_quarto AS quarto,
                       DATE_FORMAT(r.entrada_prevista, '%d/%m/%Y') AS entrada,
                       DATE_FORMAT(r.saida_prevista, '%d/%m/%Y') AS saida,
                       r.status
                FROM RESERVA r
                INNER JOIN PESSOA p ON p.cpf = r.cpf_hospede
                INNER JOIN QUARTO q ON q.numero = r.numero_quarto
                WHERE""" + FILTRO_RESERVA + """
                ORDER BY r.data_reserva DESC, r.id_reserva DESC
                LIMIT 8
                """, filtro.parametros());
    }

    private static long numero(Object valor) {
        return valor == null ? 0 : ((Number) valor).longValue();
    }
}
