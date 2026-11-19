// =====================================================================
// dashboard.js: dashboard estatístico com filtros de período e tipo.
// Todos os números vêm de GET /api/dashboard (calculados no MySQL).
// =====================================================================

const dashboard = { tipos: [] };

telas.dashboard = {
    iniciar() {
        document.getElementById("aplicarFiltro").addEventListener("click", carregarDashboard);
        document.getElementById("filtroTipo").addEventListener("change", carregarDashboard);
        document.querySelectorAll("[data-preset]").forEach(botao => {
            botao.addEventListener("click", () => {
                aplicarPeriodo(botao.dataset.preset);
                carregarDashboard();
            });
        });
    },
    async abrir() {
        await carregarTiposDoFiltro();
        await carregarDashboard();
    }
};

function aplicarPeriodo(preset) {
    const hoje = new Date().toISOString().slice(0, 10);
    const periodos = {
        tudo: ["2026-01-01", "2027-12-31"],
        s1: ["2026-01-01", "2026-06-30"],
        s2: ["2026-07-01", "2026-12-31"],
        futuro: [hoje, "2027-12-31"]
    };
    const [de, ate] = periodos[preset];
    document.getElementById("filtroDe").value = de;
    document.getElementById("filtroAte").value = ate;
}

async function carregarTiposDoFiltro() {
    if (dashboard.tipos.length) return;
    try {
        dashboard.tipos = await api("/api/tipos-quarto");
        const select = document.getElementById("filtroTipo");
        select.innerHTML = '<option value="0">Todos os tipos</option>' +
            dashboard.tipos.map(t => `<option value="${t.id_tipo}">${html(t.nome)}</option>`).join("");
    } catch (erro) {
        aviso(erro.message, true);
    }
}

async function carregarDashboard() {
    const filtro = new URLSearchParams({
        de: document.getElementById("filtroDe").value,
        ate: document.getElementById("filtroAte").value,
        tipo: document.getElementById("filtroTipo").value
    });
    try {
        const dados = await api(`/api/dashboard?${filtro}`);
        mostrarIndicadores(dados);
        mostrarEstatisticas(dados.estatisticas, dados.correlacoes);
        desenharGraficosDoDashboard(dados);
        mostrarReservasRecentes(dados.ultimasReservas);
    } catch (erro) {
        aviso(erro.message, true);
    }
}

// ---------------------------------------------------------------- indicadores

function mostrarIndicadores(d) {
    const i = d.indicadores;
    const definir = (id, texto) => (document.getElementById(id).textContent = texto);

    definir("mReservas", numero(i.reservas));
    definir("mReservasSub", `${numero(i.canceladas)} canceladas · ${numero(i.taxa_cancelamento, 1)}% de cancelamento`);
    definir("mReceita", moeda(i.receitaRecebida));
    definir("mReceitaSub", tendenciaDaReceita(d.receitaMensal));
    definir("mTicket", moeda(i.ticket_medio));
    definir("mTicketSub", `Média por reserva · ${numero(i.noites_media, 1)} noites em média`);
    definir("mOcupacao", `${numero(i.taxa_ocupacao, 1)}%`);
    definir("mOcupacaoSub", `${numero(i.ocupados)} ocupados de ${numero(i.quartos)} quartos`);

    const mini = [
        ["Hóspedes cadastrados", numero(i.hospedes)],
        ["Quartos disponíveis agora", numero(i.disponiveis)],
        ["Hóspedes por reserva (média)", numero(i.hospedes_media, 1)],
        ["Noites por reserva (média)", numero(i.noites_media, 1)],
        ["Reservas canceladas", numero(i.canceladas)],
        ["Meses com receita", numero(d.receitaMensal.length)]
    ];
    document.getElementById("miniMetrics").innerHTML = mini
        .map(([nome, valor]) => `<div><span>${html(nome)}</span><strong>${html(valor)}</strong></div>`).join("");
}

// Compara os dois últimos meses com receita: tendência de alta ou de queda.
function tendenciaDaReceita(meses) {
    if (meses.length < 2) return "Sem meses suficientes para tendência";
    const ultimo = Number(meses[meses.length - 1].valor);
    const anterior = Number(meses[meses.length - 2].valor);
    if (!anterior) return "Sem base de comparação";
    const variacao = ((ultimo - anterior) / anterior) * 100;
    const seta = variacao >= 0 ? "▲" : "▼";
    return `${seta} ${numero(Math.abs(variacao), 1)}% em ${mesCurto(meses[meses.length - 1].label)} vs ${mesCurto(meses[meses.length - 2].label)}`;
}

function mostrarEstatisticas(e, c) {
    const itens = [
        ["n (reservas)", numero(e.n)],
        ["Média", moeda(e.media)],
        ["Mediana", moeda(e.mediana)],
        ["Moda", e.moda_frequencia > 1 ? `${moeda(e.moda)} (${e.moda_frequencia}x)` : "Sem moda"],
        ["Variância", numero(e.variancia, 2)],
        ["Desvio padrão", moeda(e.desvio_padrao)],
        ["Coef. de variação", `${numero(e.coef_variacao, 1)}%`],
        ["Mínimo", moeda(e.minimo)],
        ["Máximo", moeda(e.maximo)],
        ["Moda de noites", e.noites_moda == null ? "—" : `${e.noites_moda} noites`],
        ["Correlação noites × valor (r)", c.r_noites_valor == null ? "—" : numero(c.r_noites_valor, 3)],
        ["Correlação hóspedes × valor (r)", c.r_hospedes_valor == null ? "—" : numero(c.r_hospedes_valor, 3)]
    ];
    document.getElementById("statsGrid").innerHTML = itens
        .map(([nome, valor]) => `<div><span>${html(nome)}</span><strong>${html(valor)}</strong></div>`).join("");
}

// ---------------------------------------------------------------- gráficos

const DIAS = ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"];
const eixoMoeda = { ticks: { callback: v => moedaCurta(v) } };
const semLegenda = { plugins: { legend: { display: false } } };

function desenharGraficosDoDashboard(d) {
    // 1. Rosca: reservas por status
    desenharGrafico("chStatus", {
        type: "doughnut",
        data: {
            labels: d.reservasPorStatus.map(r => rotulo(r.label)),
            datasets: [{ data: d.reservasPorStatus.map(r => r.valor), backgroundColor: d.reservasPorStatus.map((r, i) => corDe(r.label, i)) }]
        },
        options: { plugins: { legend: { position: "right" } } }
    });

    // 2. Histograma do valor das reservas
    desenharGrafico("chHistograma", {
        type: "bar",
        data: {
            labels: d.distribuicaoValores.map(r => r.label),
            datasets: [{ label: "Reservas", data: d.distribuicaoValores.map(r => r.valor), backgroundColor: "#173b53", barPercentage: 1, categoryPercentage: 0.95 }]
        },
        options: { ...semLegenda, scales: { y: { beginAtZero: true, ticks: { precision: 0 }, title: { display: true, text: "frequência" } } } }
    });

    // 3. Tendência mensal: receita (linha) + reservas (barras), dois eixos
    const meses = [...new Set([...d.receitaMensal.map(r => r.label), ...d.reservasMensais.map(r => r.label)])].sort();
    const porMes = lista => meses.map(m => Number((lista.find(r => r.label === m) || {}).valor || 0));
    desenharGrafico("chTendencia", {
        data: {
            labels: meses.map(mesCurto),
            datasets: [
                { type: "line", label: "Receita recebida", data: porMes(d.receitaMensal), borderColor: "#258c89", backgroundColor: "rgba(37,140,137,.15)", fill: true, tension: .3, yAxisID: "y" },
                { type: "bar", label: "Reservas (entrada no mês)", data: porMes(d.reservasMensais), backgroundColor: "rgba(228,166,74,.75)", yAxisID: "y1" }
            ]
        },
        options: {
            interaction: { mode: "index", intersect: false },
            plugins: { tooltip: { callbacks: { label: ctx => ctx.dataset.yAxisID === "y" ? `Receita: ${moeda(ctx.raw)}` : `Reservas: ${ctx.raw}` } } },
            scales: {
                y: { ...eixoMoeda, beginAtZero: true, position: "left" },
                y1: { beginAtZero: true, position: "right", grid: { drawOnChartArea: false }, ticks: { precision: 0 } }
            }
        }
    });

    // 4. Barras horizontais: reservas por tipo (clique filtra o dashboard por aquele tipo)
    desenharGrafico("chTipos", {
        type: "bar",
        data: {
            labels: d.reservasPorTipo.map(r => r.label),
            datasets: [{ label: "Reservas", data: d.reservasPorTipo.map(r => r.valor), backgroundColor: "#258c89" }]
        },
        options: {
            ...semLegenda,
            indexAxis: "y",
            scales: { x: { beginAtZero: true, ticks: { precision: 0 } } },
            onClick: (evento, elementos) => {
                if (!elementos.length) return;
                const nome = d.reservasPorTipo[elementos[0].index].label;
                const tipo = dashboard.tipos.find(t => t.nome === nome);
                if (tipo) {
                    document.getElementById("filtroTipo").value = tipo.id_tipo;
                    aviso(`Filtrando pelo tipo "${nome}".`);
                    carregarDashboard();
                }
            }
        }
    });

    // 5. Dispersão: noites x valor (correlação)
    const r = d.correlacoes.r_noites_valor;
    document.getElementById("chDispersaoSub").textContent =
        `Cada ponto é uma reserva · Pearson r = ${r == null ? "—" : numero(r, 3)} (${forcaDaCorrelacao(r)})`;
    desenharGrafico("chDispersao", {
        type: "scatter",
        data: { datasets: [{ label: "Reservas", data: d.dispersao.map(p => ({ x: Number(p.x), y: Number(p.y) })), backgroundColor: "rgba(23,59,83,.6)" }] },
        options: {
            ...semLegenda,
            plugins: { legend: { display: false }, tooltip: { callbacks: { label: ctx => `${ctx.raw.x} noites · ${moeda(ctx.raw.y)}` } } },
            scales: { x: { title: { display: true, text: "noites" }, ticks: { precision: 0 } }, y: { ...eixoMoeda, title: { display: true, text: "valor da reserva" } } }
        }
    });

    // 6. Barras: serviços com maior receita
    desenharGrafico("chServicos", {
        type: "bar",
        data: {
            labels: d.servicos.map(s => s.label),
            datasets: [{ label: "Receita", data: d.servicos.map(s => s.valor), backgroundColor: "#e4a64a" }]
        },
        options: { ...semLegenda, scales: { y: { ...eixoMoeda, beginAtZero: true } } }
    });

    // 7. Radar: entradas por dia da semana
    const porDia = DIAS.map((_, i) => Number((d.diasDaSemana.find(x => Number(x.dia) === i + 1) || {}).valor || 0));
    desenharGrafico("chSemana", {
        type: "radar",
        data: { labels: DIAS, datasets: [{ label: "Entradas", data: porDia, borderColor: "#d66e5c", backgroundColor: "rgba(214,110,92,.2)", pointBackgroundColor: "#d66e5c" }] },
        options: { ...semLegenda, scales: { r: { beginAtZero: true, ticks: { precision: 0 } } } }
    });

    // 8. Pizza: formas de pagamento
    desenharGrafico("chFormas", {
        type: "pie",
        data: {
            labels: d.formasPagamento.map(f => rotulo(f.label)),
            datasets: [{ data: d.formasPagamento.map(f => f.valor), backgroundColor: d.formasPagamento.map((f, i) => CORES[i % CORES.length]) }]
        },
        options: { plugins: { legend: { position: "right" }, tooltip: { callbacks: { label: ctx => `${ctx.label}: ${moeda(ctx.raw)}` } } } }
    });
}

function forcaDaCorrelacao(r) {
    if (r == null) return "sem dados";
    const a = Math.abs(r);
    const forca = a >= .7 ? "forte" : a >= .4 ? "moderada" : a >= .2 ? "fraca" : "muito fraca";
    return `${forca}, ${r >= 0 ? "positiva" : "negativa"}`;
}

function mostrarReservasRecentes(linhas) {
    const corpo = document.getElementById("recentReservations");
    corpo.innerHTML = linhas.length ? linhas.map(r => `
        <tr>
            <td>#${html(r.id_reserva)}</td>
            <td><strong>${html(r.hospede)}</strong></td>
            <td>${html(r.quarto)}</td>
            <td>${html(r.entrada)}</td>
            <td>${html(r.saida)}</td>
            <td>${etiqueta(r.status)}</td>
        </tr>`).join("") : linhaVazia(6, "Nenhuma reserva no filtro.");
}
