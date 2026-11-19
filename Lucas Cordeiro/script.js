let tutores = [];
let pets = [];
let agendamentos = [];
let csrfToken = "";
let registroEmEdicao = null;

function mostrarPagina(pageId) {
    const page = document.getElementById(pageId);
    if (!page || !page.classList.contains("pagina")) return;
    document.querySelectorAll(".pagina").forEach(function(item) {
        item.classList.toggle("escondido", item !== page);
    });
    document.querySelectorAll("[data-pagina]").forEach(function(button) {
        const selected = button.dataset.pagina === pageId;
        button.classList.toggle("ativo", selected);
        if (selected) button.setAttribute("aria-current", "page");
        else button.removeAttribute("aria-current");
    });
}

function mensagemDeErro(error) {
    return error && error.message
        ? error.message
        : "Não foi possível concluir a solicitação.";
}

async function requisitar(url, opcoes) {
    const options = { ...opcoes };
    const headers = new Headers(options.headers || {});
    if (options.body && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }
    if (csrfToken && options.method && options.method !== "GET") {
        headers.set("X-CSRF-Token", csrfToken);
    }
    options.headers = headers;

    let response;
    try {
        response = await fetch(url, options);
    } catch (error) {
        throw new Error("Não foi possível conectar ao backend. Inicie o site pelo VS Code.");
    }
    const contentType = response.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) {
        throw new Error("O servidor respondeu em um formato inesperado. Tente novamente.");
    }
    const data = await response.json();
    if (response.status === 401) {
        window.location.replace("/login");
        throw new Error("Sua sessão expirou. Entre novamente.");
    }
    if (!response.ok) {
        throw new Error(data.erro || "A solicitação não foi concluída.");
    }
    return data;
}

function exibirMensagem(element, text, isError) {
    element.textContent = text;
    element.classList.toggle("erro", Boolean(isError));
}

function criarOpcao(value, text) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = text;
    return option;
}

function atualizarSelecao(element, records, emptyLabel, format) {
    element.replaceChildren(criarOpcao("", emptyLabel));
    records.forEach(function(record) {
        element.append(criarOpcao(record.id, format(record)));
    });
    element.disabled = records.length === 0;
}

function limparEdicao() {
    if (!registroEmEdicao) return;
    const form = document.getElementById("form-" + registroEmEdicao.tipo);
    const submit = form.querySelector('button[type="submit"]');
    submit.textContent = registroEmEdicao.labelOriginal;
    form.reset();
    if (registroEmEdicao.tipo === "tutor") {
        document.getElementById("tutor-cpf").disabled = false;
    }
    registroEmEdicao = null;
}

function iniciarEdicao(tipo, record) {
    limparEdicao();
    const form = document.getElementById("form-" + tipo);
    const submit = form.querySelector('button[type="submit"]');
    registroEmEdicao = {
        tipo: tipo,
        id: record.id,
        registro: record,
        labelOriginal: submit.textContent
    };
    if (tipo === "tutor") {
        form.elements.nome.value = record.nome;
        form.elements.telefone.value = record.telefone;
        document.getElementById("tutor-cpf").disabled = true;
    } else if (tipo === "pet") {
        form.elements.tutorId.value = record.tutorId;
        form.elements.nome.value = record.nome;
        form.elements.especie.value = record.especie;
        form.elements.raca.value = record.raca || "";
    } else {
        form.elements.petId.value = record.petId;
        form.elements.data.value = record.data;
        form.elements.hora.value = record.horario.slice(0, 5);
        form.elements.motivo.value = record.motivo;
    }
    submit.textContent = "Salvar alterações";
    mostrarPagina(tipo === "tutor" ? "tutores" : tipo === "pet" ? "pets" : "agendamentos");
    form.scrollIntoView({ behavior: "smooth", block: "start" });
}

function criarLinha(tipo, record, route) {
    const row = document.createElement("li");
    row.className = "registro";
    const info = document.createElement("div");
    const name = document.createElement("strong");
    const details = document.createElement("span");

    if (tipo === "tutores") {
        name.textContent = record.nome;
        details.textContent = "Telefone: " + record.telefone + " · CPF protegido no banco";
    } else if (tipo === "pets") {
        name.textContent = record.nome;
        details.textContent = record.especie + " · " + (record.raca || "Raça não informada")
            + " · Tutor: " + record.tutorNome;
    } else {
        name.textContent = record.pet;
        const date = new Intl.DateTimeFormat("pt-BR").format(new Date(record.data + "T00:00:00"));
        details.textContent = date + " às " + record.horario + " · " + record.motivo + " · " + record.status;
    }
    info.append(name, details);

    const actions = document.createElement("div");
    actions.className = "registro-acoes";
    const edit = document.createElement("button");
    edit.type = "button";
    edit.className = "editar";
    edit.textContent = "Editar";
    edit.addEventListener("click", function() {
        const formType = tipo === "tutores" ? "tutor" : tipo === "pets" ? "pet" : "agendamento";
        iniciarEdicao(formType, record);
    });
    actions.append(edit);

    if (tipo === "tutores") {
        const invite = document.createElement("button");
        invite.type = "button";
        invite.className = "convidar";
        invite.textContent = "Gerar convite";
        invite.addEventListener("click", async function() {
            try {
                const invitation = await requisitar(
                    route + "/" + record.id + "/convite",
                    { method: "POST" }
                );
                document.getElementById("codigo-convite").value = invitation.codigo;
                document.getElementById("expiracao-convite").textContent =
                    "Válido até " + new Intl.DateTimeFormat("pt-BR", {
                        dateStyle: "short",
                        timeStyle: "short"
                    }).format(new Date(invitation.expiraEm));
                document.getElementById("copiar-convite").textContent = "Copiar código";
                document.getElementById("dialog-convite").showModal();
            } catch (error) {
                window.alert(mensagemDeErro(error));
            }
        });
        actions.append(invite);
    }

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "remover";
    remove.textContent = tipo === "agendamentos" ? "Cancelar" : "Remover";
    remove.addEventListener("click", async function() {
        const question = tipo === "agendamentos"
            ? "Deseja cancelar este agendamento?"
            : "Deseja remover este registro?";
        if (!window.confirm(question)) return;
        try {
            await requisitar(route + "/" + record.id, { method: "DELETE" });
            await carregarDados();
        } catch (error) {
            window.alert(mensagemDeErro(error));
        }
    });
    actions.append(remove);
    row.append(info, actions);
    return row;
}

function renderizarLista(tipo, records, route) {
    const list = document.getElementById("lista-" + tipo);
    list.replaceChildren();
    if (records.length === 0) {
        const empty = document.createElement("li");
        empty.className = "lista-vazia";
        empty.textContent = "Nenhum registro cadastrado.";
        list.append(empty);
    } else {
        records.forEach(function(record) {
            list.append(criarLinha(tipo, record, route));
        });
    }
    document.getElementById("total-" + tipo).textContent = records.length;
}

function atualizarTela() {
    renderizarLista("tutores", tutores, "/api/tutores");
    renderizarLista("pets", pets, "/api/pets");
    renderizarLista("agendamentos", agendamentos, "/api/agendamentos");
    atualizarSelecao(
        document.getElementById("pet-tutor"),
        tutores,
        tutores.length ? "Selecione o tutor" : "Cadastre um tutor primeiro",
        function(tutor) { return tutor.nome; }
    );
    atualizarSelecao(
        document.getElementById("agendamento-pet"),
        pets,
        pets.length ? "Selecione o pet" : "Cadastre um pet primeiro",
        function(pet) { return pet.nome + " — " + pet.tutorNome; }
    );
}

async function carregarDados() {
    const status = document.getElementById("api-status");
    exibirMensagem(status, "Carregando dados do banco...", false);
    [tutores, pets, agendamentos] = await Promise.all([
        requisitar("/api/tutores", { method: "GET" }),
        requisitar("/api/pets", { method: "GET" }),
        requisitar("/api/agendamentos", { method: "GET" })
    ]);
    atualizarTela();
    exibirMensagem(status, "Conectado ao banco de dados.", false);
}

function prepararDados(tipo, fields) {
    if (tipo === "pet") {
        return { ...fields, tutorId: Number(fields.tutorId) };
    }
    if (tipo === "agendamento") {
        const data = { ...fields, petId: Number(fields.petId), horario: fields.hora };
        if (registroEmEdicao) data.status = registroEmEdicao.registro.status;
        return data;
    }
    return fields;
}

function configurarFormulario(tipo, route, messageId) {
    const form = document.getElementById("form-" + tipo);
    form.addEventListener("submit", async function(event) {
        event.preventDefault();
        const button = form.querySelector('button[type="submit"]');
        const message = document.getElementById(messageId);
        const edit = registroEmEdicao && registroEmEdicao.tipo === tipo
            ? registroEmEdicao
            : null;
        button.disabled = true;
        exibirMensagem(message, "Salvando no banco de dados...", false);
        try {
            const fields = Object.fromEntries(new FormData(form).entries());
            const url = edit ? route + "/" + edit.id : route;
            await requisitar(url, {
                method: edit ? "PUT" : "POST",
                body: JSON.stringify(prepararDados(tipo, fields))
            });
            limparEdicao();
            form.reset();
            await carregarDados();
            exibirMensagem(message, "Dados salvos com sucesso.", false);
        } catch (error) {
            exibirMensagem(message, mensagemDeErro(error), true);
        } finally {
            button.disabled = false;
        }
    });
}

document.addEventListener("DOMContentLoaded", async function() {
    document.querySelectorAll("[data-pagina]").forEach(function(button) {
        button.addEventListener("click", function(event) {
            event.preventDefault();
            mostrarPagina(button.dataset.pagina);
        });
    });
    document.getElementById("sair").addEventListener("click", async function() {
        try {
            await requisitar("/api/auth/logout", { method: "POST" });
        } catch (error) {
            window.alert(mensagemDeErro(error));
        }
        window.location.replace("/login");
    });
    document.getElementById("fechar-convite").addEventListener("click", function() {
        document.getElementById("dialog-convite").close();
    });
    document.getElementById("dialog-convite").addEventListener("close", function() {
        document.getElementById("codigo-convite").value = "";
        document.getElementById("expiracao-convite").textContent = "";
    });
    document.getElementById("copiar-convite").addEventListener("click", async function() {
        const input = document.getElementById("codigo-convite");
        if (!navigator.clipboard || !navigator.clipboard.writeText) {
            input.select();
            window.alert("Código selecionado. Copie-o com Ctrl+C.");
            return;
        }
        try {
            await navigator.clipboard.writeText(input.value);
            this.textContent = "Código copiado";
        } catch {
            input.select();
            window.alert("Não foi possível copiar automaticamente. Copie o código selecionado com Ctrl+C.");
        }
    });

    configurarFormulario("tutor", "/api/tutores", "mensagem-tutor");
    configurarFormulario("pet", "/api/pets", "mensagem-pet");
    configurarFormulario("agendamento", "/api/agendamentos", "mensagem-agendamento");

    const currentDate = new Date();
    document.getElementById("agendamento-data").min = [
        currentDate.getFullYear(),
        String(currentDate.getMonth() + 1).padStart(2, "0"),
        String(currentDate.getDate()).padStart(2, "0")
    ].join("-");

    try {
        const session = await requisitar("/api/auth/me", { method: "GET" });
        if (session.usuario.perfil === "CLIENTE") {
            window.location.replace("/cliente");
            return;
        }
        csrfToken = session.csrfToken;
        document.getElementById("nome-funcionario").textContent = session.usuario.nome;
        await carregarDados();
    } catch (error) {
        exibirMensagem(document.getElementById("api-status"), mensagemDeErro(error), true);
    }
});
