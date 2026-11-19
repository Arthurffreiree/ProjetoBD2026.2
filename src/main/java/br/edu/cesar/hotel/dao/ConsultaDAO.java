package br.edu.cesar.hotel.dao;

import br.edu.cesar.hotel.util.JdbcUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Consultas e visões exibidas na tela "Consultas e visões":
 *   Etapa 03 -> 9 consultas gerenciais (database/consultas_etapa03.sql)
 *   Etapa 04 -> 4 consultas avançadas (database/consultas_etapa04.sql)
 *   Visões   -> vw_reserva_completa e vw_contas_a_receber (database/03_visoes.sql)
 *
 * Cada consulta guarda o SQL, os parâmetros que a tela mostra como filtros,
 * a ordem em que esses parâmetros preenchem os "?" e o gráfico sugerido.
 */
public class ConsultaDAO {

    /** Filtro mostrado na tela. tipo: "data", "inteiro" ou "opcao". padrao "hoje+N" = hoje mais N dias. */
    public record Parametro(String nome, String rotulo, String tipo, String padrao, List<String> opcoes) {
    }

    /** Gráfico da consulta: tipo (bar, line, pie), coluna do rótulo e coluna do valor (null = contar linhas). */
    public record Grafico(String tipo, String rotulo, String valor) {
    }

    public record Consulta(String id, String grupo, String titulo, String descricao, String conceitos,
                           String sql, List<Parametro> parametros, List<String> ordemParametros,
                           Grafico grafico) {
    }

    private static final List<Consulta> CONSULTAS = List.of(
            new Consulta("ocupacao-tipo", "Etapa 03",
                    "Ocupação atual por tipo de quarto",
                    "Quartos existentes e ocupados em cada tipo, comparados com a ocupação geral do hotel.",
                    "LEFT JOIN, CASE, GROUP BY, NULLIF e subconsulta escalar",
                    """
                    SELECT
                        tq.nome AS tipo_quarto,
                        COUNT(q.numero) AS total_quartos,
                        SUM(CASE WHEN q.status = 'OCUPADO' THEN 1 ELSE 0 END) AS quartos_ocupados,
                        ROUND(100.0 * SUM(CASE WHEN q.status = 'OCUPADO' THEN 1 ELSE 0 END)
                              / NULLIF(COUNT(q.numero), 0), 2) AS taxa_ocupacao_percentual,
                        (SELECT ROUND(100.0 * SUM(CASE WHEN q2.status = 'OCUPADO' THEN 1 ELSE 0 END) / COUNT(*), 2)
                           FROM QUARTO q2) AS ocupacao_geral_percentual
                    FROM TIPO_QUARTO tq
                    LEFT JOIN QUARTO q ON q.id_tipo = tq.id_tipo
                    GROUP BY tq.id_tipo, tq.nome
                    ORDER BY taxa_ocupacao_percentual DESC, total_quartos DESC, tq.nome
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("bar", "tipo_quarto", "taxa_ocupacao_percentual")),
            new Consulta("receita-mensal", "Etapa 03",
                    "Receita recebida por mês",
                    "Parcelas efetivamente pagas, agrupadas pelo mês do pagamento, com a participação no total.",
                    "INNER JOIN, DATE_FORMAT, COUNT DISTINCT, GROUP BY e subconsulta escalar",
                    """
                    SELECT
                        DATE_FORMAT(pa.data_pagamento, '%Y-%m') AS mes,
                        COUNT(DISTINCT pg.id_reserva) AS reservas_pagantes,
                        COUNT(*) AS parcelas_pagas,
                        SUM(pa.valor) AS receita_recebida,
                        ROUND(100.0 * SUM(pa.valor)
                              / (SELECT SUM(pa2.valor) FROM PARCELA pa2 WHERE pa2.status = 'PAGA'), 2) AS percentual_do_total
                    FROM PARCELA pa
                    INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
                    WHERE pa.status = 'PAGA'
                    GROUP BY DATE_FORMAT(pa.data_pagamento, '%Y-%m')
                    ORDER BY mes
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("line", "mes", "receita_recebida")),
            new Consulta("parcelas-atraso", "Etapa 03",
                    "Parcelas em atraso",
                    "Parcelas vencidas com hóspede, quarto, dias de atraso e o total que o hóspede ainda deve.",
                    "Múltiplos INNER JOINs, DATEDIFF e subconsulta correlacionada",
                    """
                    SELECT
                        pe.nome AS hospede,
                        r.id_reserva,
                        r.numero_quarto,
                        pa.numero_parcela,
                        pa.data_vencimento,
                        pa.valor,
                        DATEDIFF(CURRENT_DATE, pa.data_vencimento) AS dias_em_atraso,
                        (SELECT SUM(pa2.valor)
                           FROM PARCELA pa2
                           INNER JOIN PAGAMENTO pg2 ON pg2.id_pagamento = pa2.id_pagamento
                           INNER JOIN RESERVA r2 ON r2.id_reserva = pg2.id_reserva
                          WHERE r2.cpf_hospede = r.cpf_hospede
                            AND pa2.status IN ('PENDENTE', 'VENCIDA')) AS total_em_aberto_hospede
                    FROM PARCELA pa
                    INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
                    INNER JOIN RESERVA r ON r.id_reserva = pg.id_reserva
                    INNER JOIN PESSOA pe ON pe.cpf = r.cpf_hospede
                    WHERE pa.status = 'VENCIDA'
                       OR (pa.status = 'PENDENTE' AND pa.data_vencimento < CURRENT_DATE)
                    ORDER BY dias_em_atraso DESC, pe.nome
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("bar", "hospede", "valor")),
            new Consulta("servicos-acima-media", "Etapa 03",
                    "Serviços com receita acima da média",
                    "Serviços cuja receita supera a receita média por serviço consumido.",
                    "INNER JOIN, GROUP BY, HAVING com subconsulta e tabela derivada",
                    """
                    SELECT
                        s.nome AS servico,
                        SUM(cs.quantidade) AS quantidade_consumida,
                        COUNT(DISTINCT cs.id_hospedagem) AS hospedagens_atendidas,
                        SUM(cs.quantidade * cs.valor_unitario) AS receita_gerada
                    FROM SERVICO s
                    INNER JOIN CONSUMO_SERVICO cs ON cs.id_servico = s.id_servico
                    GROUP BY s.id_servico, s.nome
                    HAVING SUM(cs.quantidade * cs.valor_unitario) > (
                        SELECT AVG(receitas.receita_por_servico)
                        FROM (SELECT SUM(cs2.quantidade * cs2.valor_unitario) AS receita_por_servico
                                FROM CONSUMO_SERVICO cs2
                               GROUP BY cs2.id_servico) AS receitas
                    )
                    ORDER BY receita_gerada DESC
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("bar", "servico", "receita_gerada")),
            new Consulta("quartos-disponiveis", "Etapa 03",
                    "Quartos disponíveis por período",
                    "Quartos fora de manutenção, com capacidade suficiente e sem reserva ativa sobreposta ao período.",
                    "INNER JOIN, parâmetros, DATEDIFF e NOT EXISTS correlacionado",
                    """
                    SELECT
                        q.numero,
                        q.andar,
                        tq.nome AS tipo_quarto,
                        tq.capacidade,
                        tq.valor_diaria,
                        tq.valor_diaria * DATEDIFF(?, ?) AS valor_estimado
                    FROM QUARTO q
                    INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
                    WHERE q.status <> 'MANUTENCAO'
                      AND tq.capacidade >= ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM RESERVA r
                          WHERE r.numero_quarto = q.numero
                            AND r.status IN ('PENDENTE', 'CONFIRMADA', 'EM_HOSPEDAGEM')
                            AND r.entrada_prevista < ?
                            AND r.saida_prevista > ?
                      )
                    ORDER BY tq.valor_diaria, q.numero
                    """,
                    List.of(
                            new Parametro("entrada", "Entrada", "data", "hoje+1", List.of()),
                            new Parametro("saida", "Saída", "data", "hoje+4", List.of()),
                            new Parametro("hospedes", "Hóspedes", "inteiro", "2", List.of())),
                    List.of("saida", "entrada", "hospedes", "saida", "entrada"),
                    new Grafico("bar", "tipo_quarto", null)),
            new Consulta("ranking-hospedes", "Etapa 03",
                    "Hóspedes que mais gastaram",
                    "Valor das hospedagens realizadas somado aos serviços consumidos (10 maiores).",
                    "INNER JOINs, LEFT JOIN com tabela derivada, COALESCE e GROUP BY",
                    """
                    SELECT
                        pe.nome AS hospede,
                        COUNT(DISTINCT r.id_reserva) AS hospedagens,
                        SUM(pg.valor_total) AS total_diarias,
                        COALESCE(SUM(cons.total_servicos), 0) AS total_servicos,
                        SUM(pg.valor_total) + COALESCE(SUM(cons.total_servicos), 0) AS gasto_total
                    FROM HOSPEDE h
                    INNER JOIN PESSOA pe ON pe.cpf = h.cpf
                    INNER JOIN RESERVA r ON r.cpf_hospede = h.cpf
                    INNER JOIN PAGAMENTO pg ON pg.id_reserva = r.id_reserva
                    LEFT JOIN (
                        SELECT hs.id_reserva, SUM(cs.quantidade * cs.valor_unitario) AS total_servicos
                        FROM HOSPEDAGEM hs
                        INNER JOIN CONSUMO_SERVICO cs ON cs.id_hospedagem = hs.id_hospedagem
                        GROUP BY hs.id_reserva
                    ) AS cons ON cons.id_reserva = r.id_reserva
                    WHERE r.status IN ('FINALIZADA', 'EM_HOSPEDAGEM')
                    GROUP BY h.cpf, pe.nome
                    ORDER BY gasto_total DESC
                    LIMIT 10
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("bar", "hospede", "gasto_total")),
            new Consulta("checkins-funcionario", "Etapa 03",
                    "Check-ins por funcionário da recepção",
                    "Check-ins de cada funcionário, com o supervisor e a comparação com a média.",
                    "Auto-relacionamento (self join), LEFT JOIN, CASE e subconsulta escalar",
                    """
                    SELECT
                        pf.nome AS funcionario,
                        f.cargo,
                        ps.nome AS supervisor,
                        COUNT(h.id_hospedagem) AS checkins_realizados,
                        CASE
                            WHEN COUNT(h.id_hospedagem) > (
                                SELECT COUNT(*) / COUNT(DISTINCT h2.cpf_funcionario_checkin)
                                FROM HOSPEDAGEM h2
                                WHERE h2.cpf_funcionario_checkin IS NOT NULL)
                            THEN 'Acima da media'
                            ELSE 'Na media ou abaixo'
                        END AS desempenho
                    FROM FUNCIONARIO f
                    INNER JOIN PESSOA pf ON pf.cpf = f.cpf
                    LEFT JOIN FUNCIONARIO sup ON sup.cpf = f.cpf_supervisor
                    LEFT JOIN PESSOA ps ON ps.cpf = sup.cpf
                    LEFT JOIN HOSPEDAGEM h ON h.cpf_funcionario_checkin = f.cpf
                    WHERE f.cargo IN ('Recepcionista', 'Supervisor de Recepcao')
                    GROUP BY f.cpf, pf.nome, f.cargo, ps.nome
                    ORDER BY checkins_realizados DESC, pf.nome
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("bar", "funcionario", "checkins_realizados")),
            new Consulta("reservas-acima-media", "Etapa 03",
                    "Reservas acima do ticket médio",
                    "Reservas não canceladas com valor maior que o ticket médio, com hóspede e tipo de quarto.",
                    "4 INNER JOINs, CROSS JOIN com tabela derivada, AVG e DATEDIFF",
                    """
                    SELECT
                        r.id_reserva,
                        pe.nome AS hospede,
                        tq.nome AS tipo_quarto,
                        r.entrada_prevista,
                        DATEDIFF(r.saida_prevista, r.entrada_prevista) AS diarias,
                        pg.valor_total,
                        ROUND(media.ticket_medio, 2) AS ticket_medio,
                        ROUND(pg.valor_total - media.ticket_medio, 2) AS valor_acima_da_media
                    FROM RESERVA r
                    INNER JOIN PESSOA pe ON pe.cpf = r.cpf_hospede
                    INNER JOIN QUARTO q ON q.numero = r.numero_quarto
                    INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
                    INNER JOIN PAGAMENTO pg ON pg.id_reserva = r.id_reserva
                    CROSS JOIN (
                        SELECT AVG(pg2.valor_total) AS ticket_medio
                        FROM PAGAMENTO pg2
                        INNER JOIN RESERVA r2 ON r2.id_reserva = pg2.id_reserva
                        WHERE r2.status <> 'CANCELADA'
                    ) AS media
                    WHERE r.status <> 'CANCELADA'
                      AND pg.valor_total > media.ticket_medio
                    ORDER BY pg.valor_total DESC
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("bar", "tipo_quarto", null)),
            new Consulta("contas-abertas", "Etapa 03",
                    "Conta parcial das hospedagens abertas",
                    "Diárias até hoje mais os serviços consumidos em cada hospedagem em andamento.",
                    "4 INNER JOINs, subconsulta correlacionada, tabela derivada e GREATEST",
                    """
                    SELECT
                        contas.*,
                        contas.valor_diarias + contas.valor_servicos AS total_parcial
                    FROM (
                        SELECT
                            hs.id_hospedagem,
                            pe.nome AS hospede,
                            r.numero_quarto,
                            tq.nome AS tipo_quarto,
                            DATE(hs.checkin_real) AS data_checkin,
                            r.saida_prevista,
                            GREATEST(DATEDIFF(CURRENT_DATE, DATE(hs.checkin_real)), 1) AS diarias_ate_hoje,
                            GREATEST(DATEDIFF(CURRENT_DATE, DATE(hs.checkin_real)), 1) * tq.valor_diaria AS valor_diarias,
                            (SELECT COALESCE(SUM(cs.quantidade * cs.valor_unitario), 0)
                               FROM CONSUMO_SERVICO cs
                              WHERE cs.id_hospedagem = hs.id_hospedagem) AS valor_servicos
                        FROM HOSPEDAGEM hs
                        INNER JOIN RESERVA r ON r.id_reserva = hs.id_reserva
                        INNER JOIN PESSOA pe ON pe.cpf = r.cpf_hospede
                        INNER JOIN QUARTO q ON q.numero = r.numero_quarto
                        INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
                        WHERE hs.status = 'ABERTA'
                    ) AS contas
                    ORDER BY total_parcial DESC
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("bar", "numero_quarto", "total_parcial")),
            new Consulta("quartos-nunca-reservados", "Etapa 04",
                    "Quartos que nunca foram reservados",
                    "Quartos sem nenhuma reserva, para a gerência revisar preço ou divulgação.",
                    "ANTI JOIN (LEFT JOIN + IS NULL)",
                    """
                    SELECT
                        q.numero,
                        q.andar,
                        tq.nome AS tipo_quarto,
                        tq.capacidade,
                        tq.valor_diaria,
                        q.status
                    FROM QUARTO q
                    INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
                    LEFT JOIN RESERVA r       ON r.numero_quarto = q.numero
                    WHERE r.id_reserva IS NULL
                    ORDER BY tq.valor_diaria DESC, q.numero
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("bar", "tipo_quarto", null)),
            new Consulta("papeis-pessoas", "Etapa 04",
                    "Hóspedes x funcionários",
                    "Quem é só hóspede, só funcionário ou os dois (funcionário que também se hospedou).",
                    "FULL OUTER JOIN emulado (LEFT JOIN UNION RIGHT JOIN), COALESCE, CASE",
                    """
                    SELECT
                        p.nome,
                        p.cpf,
                        CASE
                            WHEN completo.cpf_hospede IS NOT NULL
                             AND completo.cpf_funcionario IS NOT NULL THEN 'HOSPEDE E FUNCIONARIO'
                            WHEN completo.cpf_hospede IS NOT NULL      THEN 'SO HOSPEDE'
                            ELSE 'SO FUNCIONARIO'
                        END AS papel,
                        completo.cargo,
                        completo.cliente_desde
                    FROM (
                        SELECT h.cpf AS cpf_hospede, f.cpf AS cpf_funcionario, f.cargo, h.data_cadastro AS cliente_desde
                        FROM HOSPEDE h
                        LEFT JOIN FUNCIONARIO f ON f.cpf = h.cpf
                        UNION
                        SELECT h.cpf AS cpf_hospede, f.cpf AS cpf_funcionario, f.cargo, h.data_cadastro AS cliente_desde
                        FROM HOSPEDE h
                        RIGHT JOIN FUNCIONARIO f ON f.cpf = h.cpf
                    ) AS completo
                    INNER JOIN PESSOA p ON p.cpf = COALESCE(completo.cpf_hospede, completo.cpf_funcionario)
                    ORDER BY papel, p.nome
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("pie", "papel", null)),
            new Consulta("quartos-acima-media-andar", "Etapa 04",
                    "Quartos que faturam acima da média do andar",
                    "Em cada andar, os quartos com receita maior que a média dos quartos daquele andar.",
                    "CTE (WITH), LEFT JOIN, GROUP BY, subconsulta correlacionada",
                    """
                    WITH receita_quarto AS (
                        SELECT q.numero, q.andar, tq.nome AS tipo_quarto,
                               COALESCE(SUM(pg.valor_total), 0) AS receita
                        FROM QUARTO q
                        INNER JOIN TIPO_QUARTO tq ON tq.id_tipo = q.id_tipo
                        LEFT JOIN RESERVA rs      ON rs.numero_quarto = q.numero
                                                 AND rs.status <> 'CANCELADA'
                        LEFT JOIN PAGAMENTO pg    ON pg.id_reserva = rs.id_reserva
                        GROUP BY q.numero, q.andar, tq.nome
                    )
                    SELECT
                        r1.andar,
                        r1.numero AS quarto,
                        r1.tipo_quarto,
                        r1.receita,
                        (SELECT ROUND(AVG(r2.receita), 2)
                           FROM receita_quarto r2
                          WHERE r2.andar = r1.andar) AS media_do_andar
                    FROM receita_quarto r1
                    WHERE r1.receita > (SELECT AVG(r2.receita)
                                          FROM receita_quarto r2
                                         WHERE r2.andar = r1.andar)
                    ORDER BY r1.andar, r1.receita DESC
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("bar", "quarto", "receita")),
            new Consulta("reservas-futuras-devedores", "Etapa 04",
                    "Reservas futuras de hóspedes com parcela atrasada",
                    "Hóspedes com reserva nos próximos dias que ainda devem parcelas vencidas. Usa os dois índices da Etapa 04.",
                    "Subconsulta com IN, subconsulta escalar correlacionada, índices",
                    """
                    SELECT
                        p.nome AS hospede,
                        r.id_reserva,
                        r.numero_quarto,
                        r.entrada_prevista,
                        r.status,
                        (SELECT SUM(pa.valor)
                           FROM PARCELA pa
                           INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
                           INNER JOIN RESERVA r2   ON r2.id_reserva = pg.id_reserva
                          WHERE r2.cpf_hospede = r.cpf_hospede
                            AND pa.status IN ('PENDENTE', 'VENCIDA')
                            AND pa.data_vencimento < CURRENT_DATE) AS valor_em_atraso
                    FROM RESERVA r
                    INNER JOIN PESSOA p ON p.cpf = r.cpf_hospede
                    WHERE r.status IN ('PENDENTE', 'CONFIRMADA')
                      AND r.entrada_prevista >= CURRENT_DATE
                      AND r.cpf_hospede IN (SELECT r3.cpf_hospede
                                              FROM PARCELA pa
                                              INNER JOIN PAGAMENTO pg ON pg.id_pagamento = pa.id_pagamento
                                              INNER JOIN RESERVA r3   ON r3.id_reserva = pg.id_reserva
                                             WHERE pa.status IN ('PENDENTE', 'VENCIDA')
                                               AND pa.data_vencimento < CURRENT_DATE)
                    ORDER BY r.entrada_prevista, p.nome
                    """,
                    List.of(),
                    List.of(),
                    new Grafico("bar", "hospede", "valor_em_atraso")),
            new Consulta("vw-reserva-completa", "Visões",
                    "Visão: reserva completa",
                    "vw_reserva_completa junta reserva, hóspede, quarto, tipo e pagamento (6 tabelas). Filtre por situação e período de entrada.",
                    "VIEW com 5 joins, subconsulta escalar, filtros por parâmetro",
                    """
                    SELECT id_reserva, status_reserva, hospede, quarto, tipo_quarto,
                           entrada_prevista, saida_prevista, diarias, valor_total, valor_pago,
                           valor_total - valor_pago AS saldo
                    FROM vw_reserva_completa
                    WHERE (? = 'TODOS' OR status_reserva = ?)
                      AND entrada_prevista BETWEEN ? AND ?
                    ORDER BY entrada_prevista DESC, id_reserva DESC
                    """,
                    List.of(
                            new Parametro("status", "Situação", "opcao", "TODOS", List.of("TODOS", "PENDENTE", "CONFIRMADA", "EM_HOSPEDAGEM", "FINALIZADA", "CANCELADA")),
                            new Parametro("de", "Entrada de", "data", "2026-01-01", List.of()),
                            new Parametro("ate", "Entrada até", "data", "2027-12-31", List.of())),
                    List.of("status", "status", "de", "ate"),
                    new Grafico("pie", "status_reserva", null)),
            new Consulta("vw-contas-a-receber", "Visões",
                    "Visão: contas a receber",
                    "vw_contas_a_receber lista as parcelas não pagas, de quem cobrar e se estão atrasadas ou a vencer.",
                    "VIEW com 4 joins, CASE, DATEDIFF, índice idx_parcela_status_vencimento",
                    """
                    SELECT hospede, email, quarto, id_reserva, numero_parcela,
                           data_vencimento, valor, situacao, dias_em_atraso
                    FROM vw_contas_a_receber
                    WHERE (? = 'TODAS' OR situacao COLLATE utf8mb4_0900_ai_ci = ?)
                    ORDER BY data_vencimento, hospede
                    """,
                    List.of(
                            new Parametro("situacao", "Situação", "opcao", "TODAS", List.of("TODAS", "ATRASADA", "A VENCER"))),
                    List.of("situacao", "situacao"),
                    new Grafico("pie", "situacao", "valor"))
    );

    public List<Consulta> listar() {
        return CONSULTAS;
    }

    public Consulta buscar(String id) {
        for (Consulta consulta : CONSULTAS) {
            if (consulta.id().equals(id)) {
                return consulta;
            }
        }
        return null;
    }

    public List<Map<String, Object>> executar(Consulta consulta, Object... parametros) throws SQLException {
        return JdbcUtils.consultar(consulta.sql(), parametros);
    }
}
