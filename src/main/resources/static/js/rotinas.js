// =====================================================================
// rotinas.js: funções, procedimentos e triggers da Etapa 05 na interface.
// =====================================================================

telas.rotinas = {
    iniciar() {
        document.getElementById("pagamentoForm").addEventListener("submit", registrarPagamento);
        document.getElementById("pgHospede").addEventListener("change", carregarParcelas);
        document.getElementById("tbHospedagens").addEventListener("click", fazerCheckout);
        document.getElementById("atualizarLog").addEventListener("click", carregarLog);
        document.getElementById("testeCapacidade").addEventListener("click", testarCapacidade);
        document.getElementById("testeOverbooking").addEventListener("click", testarOverbooking);
    },
    abrir() {
        carregarCategorias();
        carregarHospedagens();
        carregarDevedores();
        carregarLog();
    }
};

// ---------------------------------------------------------------- fn_categoria_hospede

async function carregarCategorias() {
    try {
        const linhas = await api("/api/rotinas/categorias");
        const ordem = ["OURO", "PRATA", "BRONZE", "NOVO"];
        const contagem = ordem.map(c => linhas.filter(l => l.categoria === c).length);
        desenharGrafico("chCategorias", {
            type: "bar",
            data: { labels: ordem.map(rotulo), datasets: [{ data: contagem, backgroundColor: ordem.map(c => CORES_STATUS[c]) }] },
            options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
        });
        document.getElementById("tbCategorias").innerHTML = linhas.map(l => `
            <tr><td>${html(l.hospede)}</td><td>${moeda(l.total_pago)}</td><td>${etiqueta(l.categoria)}</td></tr>`).join("");
    } catch (erro) {
        aviso(erro.message, true);
    }
}

// ---------------------------------------------------------------- fn_valor_conta + sp_realizar_checkout

async function carregarHospedagens() {
    try {
        const linhas = await api("/api/rotinas/hospedagens");
        document.getElementById("tbHospedagens").innerHTML = linhas.length ? linhas.map(l => `
            <tr>
                <td>#${html(l.id_hospedagem)}</td>
                <td><strong>${html(l.hospede)}</strong></td>
                <td>${html(l.quarto)}</td>
                <td>${dataBr(l.checkin)}</td>
                <td>${dataBr(l.saida_prevista)}</td>
                <td><strong>${moeda(l.valor_conta)}</strong></td>
                <td><button class="row-action" data-checkout="${html(l.id_hospedagem)}" data-quarto="${html(l.quarto)}">Fazer check-out</button></td>
            </tr>`).join("") : linhaVazia(7, "Nenhuma hospedagem em andamento.");
    } catch (erro) {
        aviso(erro.message, true);
    }
}

async function fazerCheckout(evento) {
    const botao = evento.target.closest("button[data-checkout]");
    if (!botao) return;
    const ok = await confirmar("Realizar check-out",
        `Chamar sp_realizar_checkout(${botao.dataset.checkout})? O quarto ${botao.dataset.quarto} vai para LIMPEZA e a reserva para FINALIZADA.`,
        "Fazer check-out");
    if (!ok) return;
    try {
        const r = await enviar("/api/rotinas/checkout", "POST", { id_hospedagem: botao.dataset.checkout });
        aviso(`${r.mensagem}: quarto ${r.quarto}, conta de ${moeda(r.valor_conta)}.`);
        carregarHospedagens();
        carregarLog();
    } catch (erro) {
        aviso(erro.message, true);
    }
}

// ---------------------------------------------------------------- sp_registrar_pagamento_hospede (cursor)

async function carregarDevedores() {
    try {
        const linhas = await api("/api/rotinas/devedores");
        const select = document.getElementById("pgHospede");
        const atual = select.value;
        select.innerHTML = linhas.map(l =>
            `<option value="${html(l.cpf)}">${html(l.nome)} · ${l.parcelas_abertas} parcela(s) · ${moeda(l.total_aberto)}</option>`).join("");
        if (linhas.some(l => l.cpf === atual)) select.value = atual;
        carregarParcelas();
    } catch (erro) {
        aviso(erro.message, true);
    }
}

async function carregarParcelas() {
    const cpf = document.getElementById("pgHospede").value;
    const corpo = document.getElementById("tbParcelas");
    if (!cpf) {
        corpo.innerHTML = linhaVazia(6, "Nenhum hóspede com parcela em aberto.");
        return;
    }
    try {
        const linhas = await api(`/api/rotinas/parcelas?cpf=${encodeURIComponent(cpf)}`);
        corpo.innerHTML = linhas.map(l => `
            <tr>
                <td>#${html(l.id_reserva)}</td>
                <td>${html(l.numero_parcela)}</td>
                <td>${dataBr(l.data_vencimento)}</td>
                <td>${moeda(l.valor)}</td>
                <td>${etiqueta(l.status)}</td>
                <td>${etiqueta(l.status_pagamento)}</td>
            </tr>`).join("");
    } catch (erro) {
        aviso(erro.message, true);
    }
}

async function registrarPagamento(evento) {
    evento.preventDefault();
    const dados = new URLSearchParams(new FormData(evento.currentTarget));
    const caixa = document.getElementById("pgResultado");
    try {
        const r = await enviar("/api/rotinas/pagamento", "POST", dados);
        caixa.className = "result-box";
        caixa.innerHTML = `CALL sp_registrar_pagamento_hospede: recebido <b>${moeda(r.valor_recebido)}</b>,
            <b>${r.parcelas_quitadas}</b> parcela(s) quitada(s), saldo restante (parâmetro OUT) <b>${moeda(r.saldo_out)}</b>.`;
        await carregarDevedores();
        carregarCategorias();
    } catch (erro) {
        caixa.className = "result-box error";
        caixa.textContent = erro.message;
    }
}

// ---------------------------------------------------------------- triggers

async function carregarLog() {
    try {
        const linhas = await api("/api/rotinas/log");
        document.getElementById("tbLog").innerHTML = linhas.length ? linhas.map(l => `
            <tr>
                <td>#${html(l.id_log)}</td>
                <td>#${html(l.id_reserva)}</td>
                <td>${html(l.hospede)}</td>
                <td>${etiqueta(l.status_anterior)}</td>
                <td>${etiqueta(l.status_novo)}</td>
                <td>${html(l.data_hora)}</td>
                <td>${html(l.usuario)}</td>
            </tr>`).join("") : linhaVazia(7, "Nenhuma mudança de status ainda. Altere o status de uma reserva em Cadastros ou faça um check-out.");
    } catch (erro) {
        aviso(erro.message, true);
    }
}

// Mostra o resultado de uma tentativa que o trigger deve recusar.
async function tentarReserva(descricao, dados) {
    const caixa = document.getElementById("triggerResultado");
    try {
        await enviar("/api/reservas", "POST", dados);
        caixa.className = "result-box";
        caixa.textContent = `${descricao}: a reserva foi aceita (nenhuma regra violada).`;
    } catch (erro) {
        caixa.className = "result-box error";
        caixa.innerHTML = `${html(descricao)}: o trigger recusou o INSERT com a mensagem <b>"${html(erro.message)}"</b>`;
    }
}

async function testarCapacidade() {
    const [quartos, hospedes] = await Promise.all([api("/api/quartos"), api("/api/reservas/opcoes")]);
    const quarto = quartos.reduce((a, b) => (b.capacidade < a.capacidade ? b : a));
    tentarReserva(`Reserva de ${quarto.capacidade + 3} pessoas no quarto ${quarto.numero} (capacidade ${quarto.capacidade})`, {
        cpf_hospede: hospedes[0].cpf, numero_quarto: quarto.numero,
        entrada_prevista: "2027-06-01", saida_prevista: "2027-06-03",
        qtd_hospedes: quarto.capacidade + 3, status: "PENDENTE"
    });
}

async function testarOverbooking() {
    const reservas = await api("/api/reservas");
    const alvo = reservas.find(r => ["CONFIRMADA", "PENDENTE", "EM_HOSPEDAGEM"].includes(r.status));
    if (!alvo) {
        aviso("Nenhuma reserva ativa para testar o conflito.", true);
        return;
    }
    tentarReserva(`Nova reserva no quarto ${alvo.numero_quarto} de ${dataBr(alvo.entrada_prevista)} a ${dataBr(alvo.saida_prevista)}, que já é da reserva #${alvo.id_reserva}`, {
        cpf_hospede: alvo.cpf_hospede, numero_quarto: alvo.numero_quarto,
        entrada_prevista: alvo.entrada_prevista, saida_prevista: alvo.saida_prevista,
        qtd_hospedes: 1, status: "PENDENTE"
    });
}
