// =====================================================================
// cadastros.js: CRUD das 4 tabelas (QUARTO, SERVICO, TIPO_QUARTO, RESERVA).
// Uma configuração por tabela descreve campos do formulário e colunas;
// o mesmo código monta o formulário, a tabela, a busca e as ações.
// =====================================================================

const STATUS_QUARTO = ["DISPONIVEL", "OCUPADO", "LIMPEZA", "MANUTENCAO"];
const STATUS_RESERVA_TELA = ["PENDENTE", "CONFIRMADA", "CANCELADA"];

// Listas usadas nos campos de seleção (carregadas sob demanda).
const opcoes = { tipos: [], quartos: [], hospedes: [] };

const entidades = {
    quartos: {
        titulo: "Quartos",
        endpoint: "/api/quartos",
        chave: "numero",
        nota: "Tabela QUARTO. Excluir um quarto com reservas é bloqueado pela chave estrangeira.",
        campos: [
            { nome: "numero", rotulo: "Número", tipo: "text", maxlength: 10, soNoCadastro: true },
            { nome: "andar", rotulo: "Andar", tipo: "number", min: 0 },
            { nome: "status", rotulo: "Status", tipo: "select", lista: () => STATUS_QUARTO.map(s => [s, rotulo(s)]) },
            { nome: "id_tipo", rotulo: "Tipo de quarto", tipo: "select", lista: () => opcoes.tipos.map(t => [t.id_tipo, `${t.nome} · ${t.capacidade} pessoa(s)`]) }
        ],
        colunas: [["numero", "Número"], ["andar", "Andar"], ["tipo", "Tipo"], ["capacidade", "Capacidade"], ["valor_diaria", "Diária"], ["status", "Status"]],
        busca: ["numero", "tipo", "status"],
        descricao: r => `o quarto ${r.numero}`,
        precisa: ["tipos"]
    },
    servicos: {
        titulo: "Serviços",
        endpoint: "/api/servicos",
        chave: "id_servico",
        nota: "Tabela SERVICO. Serviço já consumido não pode ser excluído (chave estrangeira).",
        campos: [
            { nome: "nome", rotulo: "Nome", tipo: "text", maxlength: 80 },
            { nome: "descricao", rotulo: "Descrição", tipo: "text", maxlength: 255 },
            { nome: "valor_atual", rotulo: "Valor atual (R$)", tipo: "number", min: 0, step: "0.01" },
            { nome: "ativo", rotulo: "Serviço ativo", tipo: "checkbox" }
        ],
        colunas: [["id_servico", "ID"], ["nome", "Serviço"], ["descricao", "Descrição"], ["valor_atual", "Valor"], ["situacao", "Situação"]],
        busca: ["nome", "descricao"],
        preparar: r => ({ ...r, situacao: r.ativo ? "ATIVA" : "INATIVA" }),
        descricao: r => `o serviço "${r.nome}"`
    },
    tipos: {
        titulo: "Tipos de quarto",
        endpoint: "/api/tipos-quarto",
        chave: "id_tipo",
        nota: "Tabela TIPO_QUARTO. O banco valida capacidade > 0, diária ≥ 0 e nome único; tipo com quartos não pode ser excluído.",
        campos: [
            { nome: "nome", rotulo: "Nome", tipo: "text", maxlength: 50 },
            { nome: "capacidade", rotulo: "Capacidade", tipo: "number", min: 1 },
            { nome: "valor_diaria", rotulo: "Valor da diária (R$)", tipo: "number", min: 0, step: "0.01" },
            { nome: "descricao", rotulo: "Descrição", tipo: "text", maxlength: 255 }
        ],
        colunas: [["id_tipo", "ID"], ["nome", "Tipo"], ["capacidade", "Capacidade"], ["valor_diaria", "Diária"], ["quantidade_quartos", "Quartos"], ["descricao", "Descrição"]],
        busca: ["nome", "descricao"],
        descricao: r => `o tipo "${r.nome}"`,
        aoSalvar: () => { opcoes.tipos = []; dashboard.tipos = []; }
    },
    reservas: {
        titulo: "Reservas",
        endpoint: "/api/reservas",
        chave: "id_reserva",
        nota: "Tabela RESERVA. Os triggers recusam capacidade excedida e overbooking; toda troca de status vai para LOG_RESERVA. Reservas em hospedagem ou finalizadas mudam pelo check-out (tela Funções e triggers).",
        campos: [
            { nome: "cpf_hospede", rotulo: "Hóspede", tipo: "select", lista: () => opcoes.hospedes.map(h => [h.cpf, h.nome]) },
            { nome: "numero_quarto", rotulo: "Quarto", tipo: "select", lista: () => opcoes.quartos.map(q => [q.numero, `${q.numero} · ${q.tipo} (até ${q.capacidade})`]) },
            { nome: "entrada_prevista", rotulo: "Entrada", tipo: "date" },
            { nome: "saida_prevista", rotulo: "Saída", tipo: "date" },
            { nome: "qtd_hospedes", rotulo: "Hóspedes", tipo: "number", min: 1, padrao: 1 },
            { nome: "status", rotulo: "Status", tipo: "select", lista: () => STATUS_RESERVA_TELA.map(s => [s, rotulo(s)]) },
            { nome: "observacao", rotulo: "Observação", tipo: "text", maxlength: 255, opcional: true }
        ],
        colunas: [["id_reserva", "ID"], ["hospede", "Hóspede"], ["numero_quarto", "Quarto"], ["tipo", "Tipo"], ["entrada_prevista", "Entrada"], ["saida_prevista", "Saída"], ["qtd_hospedes", "Hóspedes"], ["status", "Status"]],
        busca: ["hospede", "numero_quarto", "status", "tipo"],
        editavel: r => STATUS_RESERVA_TELA.includes(r.status),
        descricao: r => `a reserva #${r.id_reserva} de ${r.hospede}`,
        precisa: ["quartos", "hospedes"]
    }
};

const crud = { atual: "quartos", linhas: [], editando: null };

telas.cadastros = {
    iniciar() {
        document.querySelectorAll("#crudTabs .tab").forEach(aba => {
            aba.addEventListener("click", () => trocarEntidade(aba.dataset.entity));
        });
        document.getElementById("novoRegistro").addEventListener("click", () => abrirFormulario(null));
        document.getElementById("crudFechar").addEventListener("click", fecharFormulario);
        document.getElementById("crudCancelar").addEventListener("click", fecharFormulario);
        document.getElementById("crudForm").addEventListener("submit", salvarRegistro);
        document.getElementById("crudBusca").addEventListener("input", desenharTabela);
        document.getElementById("crudBody").addEventListener("click", acaoDaTabela);
    },
    abrir() {
        carregarEntidade();
    }
};

function trocarEntidade(nome) {
    crud.atual = nome;
    document.querySelectorAll("#crudTabs .tab").forEach(a => a.classList.toggle("active", a.dataset.entity === nome));
    document.getElementById("crudBusca").value = "";
    fecharFormulario();
    carregarEntidade();
}

async function carregarOpcoes(lista) {
    if (lista.includes("tipos") && !opcoes.tipos.length) opcoes.tipos = await api("/api/tipos-quarto");
    if (lista.includes("quartos") && !opcoes.quartos.length) opcoes.quartos = await api("/api/quartos");
    if (lista.includes("hospedes") && !opcoes.hospedes.length) opcoes.hospedes = await api("/api/reservas/opcoes");
}

async function carregarEntidade() {
    const e = entidades[crud.atual];
    document.getElementById("crudTitulo").textContent = e.titulo;
    document.getElementById("crudNote").textContent = e.nota;
    try {
        await carregarOpcoes(e.precisa || []);
        const linhas = await api(e.endpoint);
        crud.linhas = e.preparar ? linhas.map(e.preparar) : linhas;
        desenharTabela();
    } catch (erro) {
        aviso(erro.message, true);
    }
}

function desenharTabela() {
    const e = entidades[crud.atual];
    const termo = document.getElementById("crudBusca").value.trim().toLowerCase();
    const linhas = crud.linhas.filter(r => !termo || e.busca.some(c => String(r[c] ?? "").toLowerCase().includes(termo)));

    document.getElementById("crudContagem").textContent = `${linhas.length} de ${crud.linhas.length} registros`;
    document.getElementById("crudHead").innerHTML =
        `<tr>${e.colunas.map(([, titulo]) => `<th>${html(titulo)}</th>`).join("")}<th>Ações</th></tr>`;
    document.getElementById("crudBody").innerHTML = linhas.length ? linhas.map(r => {
        const podeEditar = !e.editavel || e.editavel(r);
        const id = html(r[e.chave]);
        return `<tr>
            ${e.colunas.map(([coluna]) => `<td>${formatarCelula(coluna, r[coluna])}</td>`).join("")}
            <td><div class="row-actions">
                ${podeEditar ? `<button class="row-action" data-acao="editar" data-id="${id}">Editar</button>` : ""}
                <button class="row-action delete" data-acao="excluir" data-id="${id}">Excluir</button>
            </div></td>
        </tr>`;
    }).join("") : linhaVazia(e.colunas.length + 1, "Nenhum registro encontrado.");
}

async function acaoDaTabela(evento) {
    const botao = evento.target.closest("button[data-acao]");
    if (!botao) return;
    const e = entidades[crud.atual];
    const registro = crud.linhas.find(r => String(r[e.chave]) === botao.dataset.id);
    if (!registro) return;

    if (botao.dataset.acao === "editar") {
        abrirFormulario(registro);
        return;
    }
    const ok = await confirmar("Confirmar exclusão", `Excluir ${e.descricao(registro)}? Registros vinculados podem impedir a exclusão.`, "Excluir");
    if (!ok) return;
    try {
        const resposta = await api(`${e.endpoint}/${encodeURIComponent(registro[e.chave])}`, { method: "DELETE" });
        aviso(resposta.mensagem);
        depoisDeSalvar(e);
    } catch (erro) {
        aviso(erro.message, true);
    }
}

function abrirFormulario(registro) {
    const e = entidades[crud.atual];
    crud.editando = registro;
    document.getElementById("crudFormTitle").textContent = registro ? `Editar ${e.descricao(registro)}` : `Novo registro: ${e.titulo}`;
    document.getElementById("crudCampos").innerHTML = e.campos.map(campo => montarCampo(campo, registro)).join("");
    const form = document.getElementById("crudForm");
    form.classList.remove("hidden");
    form.scrollIntoView({ behavior: "smooth", block: "start" });
}

function montarCampo(c, registro) {
    const valor = registro ? registro[c.nome] : c.padrao;
    const travado = registro && c.soNoCadastro ? "disabled" : "";
    const obrigatorio = c.opcional ? "" : "required";
    if (c.tipo === "checkbox") {
        const marcado = registro ? registro[c.nome] : true;
        return `<label class="toggle-label"><input name="${c.nome}" type="checkbox" ${marcado ? "checked" : ""}><span>${html(c.rotulo)}</span></label>`;
    }
    if (c.tipo === "select") {
        const itens = c.lista().map(([v, texto]) =>
            `<option value="${html(v)}" ${String(v) === String(valor) ? "selected" : ""}>${html(texto)}</option>`).join("");
        return `<label>${html(c.rotulo)}<select name="${c.nome}" ${obrigatorio} ${travado}>${itens}</select></label>`;
    }
    const extras = [
        c.maxlength ? `maxlength="${c.maxlength}"` : "",
        c.min !== undefined ? `min="${c.min}"` : "",
        c.step ? `step="${c.step}"` : ""
    ].join(" ");
    return `<label>${html(c.rotulo)}<input name="${c.nome}" type="${c.tipo}" value="${html(valor ?? "")}" ${extras} ${obrigatorio} ${travado}></label>`;
}

async function salvarRegistro(evento) {
    evento.preventDefault();
    const e = entidades[crud.atual];
    const form = evento.currentTarget;
    const dados = new URLSearchParams(new FormData(form));
    e.campos.filter(c => c.tipo === "checkbox").forEach(c => dados.set(c.nome, form.elements[c.nome].checked ? "true" : "false"));
    const editando = crud.editando;
    if (editando) {
        e.campos.filter(c => c.soNoCadastro).forEach(c => dados.set(c.nome, editando[c.nome]));
    }
    try {
        const url = editando ? `${e.endpoint}/${encodeURIComponent(editando[e.chave])}` : e.endpoint;
        const resposta = await enviar(url, editando ? "PUT" : "POST", dados);
        aviso(resposta.mensagem);
        fecharFormulario();
        depoisDeSalvar(e);
    } catch (erro) {
        aviso(erro.message, true);
    }
}

function depoisDeSalvar(e) {
    if (e.aoSalvar) e.aoSalvar();
    opcoes.quartos = [];
    carregarEntidade();
}

function fecharFormulario() {
    crud.editando = null;
    const form = document.getElementById("crudForm");
    form.classList.add("hidden");
    form.reset();
}
