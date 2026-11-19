// =====================================================================
// consultas.js: as 13 consultas (Etapas 03 e 04) e as 2 visões.
// Cada uma mostra filtros (quando tem), indicadores, gráfico, tabela e SQL.
// =====================================================================

const consultas = { lista: [], atual: null };

telas.consultas = {
    iniciar() {
        document.getElementById("runQuery").addEventListener("click", executarConsulta);
        document.getElementById("queryFilters").addEventListener("submit", evento => {
            evento.preventDefault();
            executarConsulta();
        });
        document.getElementById("queryList").addEventListener("click", evento => {
            const botao = evento.target.closest("button[data-query]");
            if (botao) selecionarConsulta(botao.dataset.query);
        });
    },
    async abrir() {
        if (!consultas.lista.length) {
            try {
                consultas.lista = await api("/api/consultas");
            } catch (erro) {
                aviso(erro.message, true);
                return;
            }
            montarListaDeConsultas();
        }
        selecionarConsulta(consultas.atual || consultas.lista[0].id);
    }
};

function montarListaDeConsultas() {
    let grupoAtual = "";
    let numeroNoGrupo = 0;
    document.getElementById("queryList").innerHTML = consultas.lista.map(c => {
        let cabecalho = "";
        if (c.grupo !== grupoAtual) {
            grupoAtual = c.grupo;
            numeroNoGrupo = 0;
            cabecalho = `<p class="query-group">${html(c.grupo)}</p>`;
        }
        numeroNoGrupo++;
        const marcador = c.grupo === "Visões" ? "VW" : String(numeroNoGrupo).padStart(2, "0");
        return `${cabecalho}
            <button class="query-option" data-query="${html(c.id)}">
                <span>${marcador}</span>
                <div><strong>${html(c.titulo)}</strong><small>${html(c.conceitos)}</small></div>
            </button>`;
    }).join("");
}

function selecionarConsulta(id) {
    const c = consultas.lista.find(x => x.id === id);
    if (!c) return;
    consultas.atual = id;
    document.querySelectorAll(".query-option").forEach(b => b.classList.toggle("active", b.dataset.query === id));
    document.getElementById("queryTitle").textContent = c.titulo;
    document.getElementById("queryDescription").textContent = c.descricao;
    document.getElementById("queryConcepts").textContent = `Conceitos: ${c.conceitos}`;
    document.getElementById("querySql").textContent = c.sql;

    // Filtros: um campo para cada parâmetro da consulta
    const form = document.getElementById("queryFilters");
    form.innerHTML = c.parametros.map(p => {
        if (p.tipo === "opcao") {
            const itens = p.opcoes.map(o => `<option value="${html(o)}" ${o === p.padrao ? "selected" : ""}>${html(rotulo(o))}</option>`).join("");
            return `<label>${html(p.rotulo)}<select name="${p.nome}">${itens}</select></label>`;
        }
        const tipo = p.tipo === "data" ? "date" : "number";
        return `<label>${html(p.rotulo)}<input name="${p.nome}" type="${tipo}" value="${html(p.padrao)}" ${tipo === "number" ? 'min="1"' : ""} required></label>`;
    }).join("") + (c.parametros.length ? '<button type="submit" class="button secondary">Filtrar</button>' : "");
    form.classList.toggle("hidden", !c.parametros.length);

    executarConsulta();
}

async function executarConsulta() {
    const c = consultas.lista.find(x => x.id === consultas.atual);
    if (!c) return;
    const form = document.getElementById("queryFilters");
    if (c.parametros.length && !form.reportValidity()) return;
    const filtros = c.parametros.length ? `?${new URLSearchParams(new FormData(form))}` : "";
    const botao = document.getElementById("runQuery");
    botao.disabled = true;
    botao.textContent = "Executando...";
    try {
        const linhas = await api(`/api/consultas/${encodeURIComponent(c.id)}${filtros}`);
        mostrarIndicadoresDaConsulta(c, linhas);
        desenharGraficoDaConsulta(c, linhas);
        mostrarTabelaDaConsulta(linhas);
    } catch (erro) {
        aviso(erro.message, true);
        mostrarIndicadoresDaConsulta(c, []);
        desenharGraficoDaConsulta(c, []);
        mostrarTabelaDaConsulta([]);
    } finally {
        botao.disabled = false;
        botao.textContent = "Executar";
    }
}

// Indicadores: quantidade de linhas e, se a consulta tem coluna de valor, total e média.
function mostrarIndicadoresDaConsulta(c, linhas) {
    const itens = [["Linhas retornadas", numero(linhas.length)]];
    const coluna = c.grafico.valor;
    if (coluna && linhas.length) {
        const valores = linhas.map(l => Number(l[coluna] || 0));
        const total = valores.reduce((a, b) => a + b, 0);
        const ehMoeda = COLUNA_MOEDA.test(coluna);
        const ehPercentual = /percentual/.test(coluna);
        const fmt = v => ehMoeda ? moeda(v) : ehPercentual ? `${numero(v, 2)}%` : numero(v, 1);
        if (!ehPercentual) itens.push([`Soma de ${rotulo(coluna)}`, fmt(total)]);
        itens.push([`Média de ${rotulo(coluna)}`, fmt(total / valores.length)]);
        itens.push([`Maior ${rotulo(coluna)}`, fmt(Math.max(...valores))]);
    } else if (linhas.length) {
        const grupos = new Set(linhas.map(l => l[c.grafico.rotulo])).size;
        itens.push([`${rotulo(c.grafico.rotulo)} diferentes`, numero(grupos)]);
    }
    document.getElementById("queryKpis").innerHTML = itens
        .map(([nome, valor]) => `<div><span>${html(nome)}</span><strong>${html(valor)}</strong></div>`).join("");
}

// Gráfico: agrupa pela coluna de rótulo; soma a coluna de valor ou conta as linhas.
function desenharGraficoDaConsulta(c, linhas) {
    const caixa = document.getElementById("queryChartBox");
    if (!linhas.length) {
        caixa.classList.add("hidden");
        return;
    }
    caixa.classList.remove("hidden");
    const grupos = new Map();
    linhas.forEach(l => {
        const chave = String(l[c.grafico.rotulo] ?? "—");
        const somar = c.grafico.valor ? Number(l[c.grafico.valor] || 0) : 1;
        grupos.set(chave, (grupos.get(chave) || 0) + somar);
    });
    let pares = [...grupos.entries()];
    if (c.grafico.tipo !== "line") pares.sort((a, b) => b[1] - a[1]);
    pares = pares.slice(0, c.grafico.tipo === "pie" ? 8 : 12);

    const tituloValor = c.grafico.valor ? rotulo(c.grafico.valor) : "Quantidade";
    const ehMoeda = c.grafico.valor && COLUNA_MOEDA.test(c.grafico.valor);
    const labels = pares.map(([k]) => c.grafico.rotulo === "mes" ? mesCurto(k) : (/^[A-Z_ ]+$/.test(k) ? rotulo(k) : k));
    const valores = pares.map(([, v]) => v);

    if (c.grafico.tipo === "pie") {
        desenharGrafico("queryChart", {
            type: "doughnut",
            data: { labels, datasets: [{ data: valores, backgroundColor: pares.map(([k], i) => corDe(k, i)) }] },
            options: { plugins: { legend: { position: "right" }, tooltip: { callbacks: { label: ctx => `${ctx.label}: ${ehMoeda ? moeda(ctx.raw) : ctx.raw}` } } } }
        });
        return;
    }
    desenharGrafico("queryChart", {
        type: c.grafico.tipo,
        data: {
            labels,
            datasets: [{
                label: tituloValor, data: valores,
                backgroundColor: c.grafico.tipo === "line" ? "rgba(37,140,137,.15)" : "#258c89",
                borderColor: "#258c89", fill: c.grafico.tipo === "line", tension: .3
            }]
        },
        options: {
            plugins: { legend: { display: false }, tooltip: { callbacks: { label: ctx => `${tituloValor}: ${ehMoeda ? moeda(ctx.raw) : numero(ctx.raw, 2)}` } } },
            scales: { y: { beginAtZero: true, ticks: { callback: v => ehMoeda ? moedaCurta(v) : v } }, x: { ticks: { autoSkip: false, maxRotation: 50, minRotation: 0 } } }
        }
    });
}

function mostrarTabelaDaConsulta(linhas) {
    const cabeca = document.querySelector("#queryTable thead");
    const corpo = document.querySelector("#queryTable tbody");
    if (!linhas.length) {
        cabeca.innerHTML = "";
        corpo.innerHTML = linhaVazia(1, "A consulta não retornou registros.");
        return;
    }
    const colunas = Object.keys(linhas[0]);
    cabeca.innerHTML = `<tr>${colunas.map(c => `<th>${html(rotulo(c))}</th>`).join("")}</tr>`;
    corpo.innerHTML = linhas.map(l => `<tr>${colunas.map(c => `<td>${formatarCelula(c, l[c])}</td>`).join("")}</tr>`).join("");
}
