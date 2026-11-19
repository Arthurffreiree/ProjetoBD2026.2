// =====================================================================
// app.js: base da interface (chamada à API, navegação, formatação,
// avisos, janela de confirmação e criação de gráficos).
// As telas ficam em dashboard.js, cadastros.js, consultas.js e rotinas.js.
// =====================================================================

const CORES = ["#173b53", "#258c89", "#e4a64a", "#d66e5c", "#8099a7", "#5b7db1", "#9b6fb0", "#6aa36f", "#c98a3d", "#4f9fb8"];
const CORES_STATUS = {
    FINALIZADA: "#2b7d64", EM_HOSPEDAGEM: "#e4a64a", CONFIRMADA: "#258c89",
    PENDENTE: "#8099a7", CANCELADA: "#d66e5c",
    ATRASADA: "#d66e5c", "A VENCER": "#258c89",
    OURO: "#d4a537", PRATA: "#9aa7b1", BRONZE: "#b0703c", NOVO: "#5b7db1"
};

// Cada tela registra aqui a função que carrega seus dados.
const telas = {};

document.addEventListener("DOMContentLoaded", () => {
    if (window.Chart) {
        Chart.defaults.font.family = "Inter, 'Segoe UI', Roboto, Arial, sans-serif";
        Chart.defaults.color = "#5a6b77";
        Chart.defaults.plugins.legend.labels.boxWidth = 12;
    }
    ligarNavegacao();
    ligarDialogo();
    verificarConexao();
    Object.values(telas).forEach(tela => tela.iniciar && tela.iniciar());
    abrirTela("dashboard");
});

// ---------------------------------------------------------------- API

// Toda chamada ao Java passa por aqui. Em erro, lança a mensagem que o Java mandou.
async function api(url, opcoes = {}) {
    const resposta = await fetch(url, opcoes);
    let corpo;
    try {
        corpo = await resposta.json();
    } catch {
        corpo = { erro: `Resposta inválida do servidor (${resposta.status}).` };
    }
    if (!resposta.ok) throw new Error(corpo.erro || "Não foi possível concluir a operação.");
    return corpo;
}

// Envia um formulário (POST/PUT) no formato application/x-www-form-urlencoded.
function enviar(url, metodo, dados) {
    return api(url, {
        method: metodo,
        headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
        body: new URLSearchParams(dados)
    });
}

// ---------------------------------------------------------------- navegação

function ligarNavegacao() {
    document.querySelectorAll(".nav-item").forEach(botao => {
        botao.addEventListener("click", () => abrirTela(botao.dataset.view));
    });
    document.getElementById("menuButton").addEventListener("click", () => document.body.classList.toggle("menu-open"));
}

function abrirTela(nome) {
    document.querySelectorAll(".nav-item").forEach(b => b.classList.toggle("active", b.dataset.view === nome));
    document.querySelectorAll(".view").forEach(s => s.classList.toggle("active", s.id === nome));
    const botao = document.querySelector(`.nav-item[data-view="${nome}"]`);
    document.getElementById("pageTitle").textContent = botao.querySelector("span:last-child").textContent;
    document.body.classList.remove("menu-open");
    if (telas[nome] && telas[nome].abrir) telas[nome].abrir();
}

async function verificarConexao() {
    const selo = document.getElementById("connectionBadge");
    try {
        const saude = await api("/api/health");
        selo.className = "connection-badge online";
        selo.innerHTML = "<span></span> MySQL conectado";
        selo.title = `${saude.banco} · ${saude.versao}`;
    } catch (erro) {
        selo.className = "connection-badge offline";
        selo.innerHTML = "<span></span> MySQL desconectado";
        selo.title = erro.message;
    }
}

// ---------------------------------------------------------------- gráficos

const graficos = {};

// Cria (ou recria) um gráfico Chart.js no canvas indicado.
function desenharGrafico(idCanvas, config) {
    if (graficos[idCanvas]) graficos[idCanvas].destroy();
    const canvas = document.getElementById(idCanvas);
    config.options = Object.assign({ responsive: true, maintainAspectRatio: false }, config.options || {});
    graficos[idCanvas] = new Chart(canvas, config);
    return graficos[idCanvas];
}

function corDe(rotulo, indice) {
    return CORES_STATUS[rotulo] || CORES[indice % CORES.length];
}

// ---------------------------------------------------------------- formatação

function moeda(valor) {
    return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Number(valor || 0));
}

function moedaCurta(valor) {
    return new Intl.NumberFormat("pt-BR", { notation: "compact", style: "currency", currency: "BRL", maximumFractionDigits: 1 }).format(valor);
}

function numero(valor, casas = 0) {
    return new Intl.NumberFormat("pt-BR", { minimumFractionDigits: casas, maximumFractionDigits: casas }).format(Number(valor || 0));
}

function dataBr(valor) {
    if (!valor) return "—";
    const [ano, mes, dia] = String(valor).slice(0, 10).split("-");
    return `${dia}/${mes}/${ano}`;
}

// "2026-03" -> "mar/26"
function mesCurto(valor) {
    const m = /^(\d{4})-(\d{2})$/.exec(String(valor));
    if (!m) return String(valor);
    const meses = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];
    return `${meses[Number(m[2]) - 1]}/${m[1].slice(2)}`;
}

// "total_em_aberto" -> "Total Em Aberto"
function rotulo(valor) {
    return String(valor ?? "").replaceAll("_", " ").toLowerCase().replace(/(^|\s)\S/g, l => l.toUpperCase());
}

// Escapa o texto antes de colocar em innerHTML (proteção contra XSS).
function html(valor) {
    return String(valor ?? "")
        .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}

function etiqueta(valor) {
    const texto = String(valor ?? "—");
    const classe = texto.toLowerCase().replaceAll("_", "-").replaceAll(" ", "-").replaceAll("ç", "c").replaceAll("ã", "a");
    return `<span class="status ${html(classe)}">${html(rotulo(texto))}</span>`;
}

const COLUNA_MOEDA = /(^|_)(valor|receita|gasto|ticket|saldo|media_do_andar)|^total_(diarias|servicos|parcial|em_aberto|pago|aberto)/;

// Formata uma célula pelo nome da coluna (apelido do SQL).
function formatarCelula(coluna, valor) {
    if (valor == null) return "—";
    if (/percentual/.test(coluna)) return `${numero(valor, 2)}%`;
    if (/^(status|situacao|categoria|papel)/.test(coluna)) return etiqueta(valor);
    if (COLUNA_MOEDA.test(coluna) && !isNaN(valor)) return moeda(valor);
    if (/^\d{4}-\d{2}-\d{2}$/.test(String(valor))) return dataBr(valor);
    if (coluna === "mes") return html(mesCurto(valor));
    return html(valor);
}

function linhaVazia(colunas, mensagem) {
    return `<tr class="empty-row"><td colspan="${colunas}">${html(mensagem)}</td></tr>`;
}

// ---------------------------------------------------------------- avisos

let tempoDoAviso;
function aviso(mensagem, erro = false) {
    const toast = document.getElementById("toast");
    toast.textContent = mensagem;
    toast.className = `toast show${erro ? " error" : ""}`;
    clearTimeout(tempoDoAviso);
    tempoDoAviso = setTimeout(() => (toast.className = "toast"), 4500);
}

// Janela de confirmação que devolve uma Promise (true = confirmou).
let resolverConfirmacao = null;
function confirmar(titulo, mensagem, textoBotao = "Confirmar") {
    document.getElementById("confirmTitle").textContent = titulo;
    document.getElementById("confirmMessage").textContent = mensagem;
    document.getElementById("confirmOk").textContent = textoBotao;
    document.getElementById("confirmDialog").classList.remove("hidden");
    return new Promise(resolver => (resolverConfirmacao = resolver));
}

function ligarDialogo() {
    const fechar = resposta => {
        document.getElementById("confirmDialog").classList.add("hidden");
        if (resolverConfirmacao) resolverConfirmacao(resposta);
        resolverConfirmacao = null;
    };
    document.getElementById("confirmOk").addEventListener("click", () => fechar(true));
    document.getElementById("confirmCancel").addEventListener("click", () => fechar(false));
}
