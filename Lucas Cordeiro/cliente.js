let csrfToken = "";
let pets = [];
let agendamentos = [];

function mostrarMensagem(elemento, texto, erro) {
    elemento.textContent = texto;
    elemento.classList.toggle("erro", Boolean(erro));
}

function mensagemDeErro(erro) {
    return erro && erro.message ? erro.message : "Não foi possível concluir a solicitação.";
}

function atualizarImagem(imagem, alternativa, temFoto, rota) {
    imagem.hidden = true;
    alternativa.hidden = false;
    imagem.onload = function() {
        imagem.hidden = false;
        alternativa.hidden = true;
    };
    imagem.onerror = function() {
        imagem.hidden = true;
        alternativa.hidden = false;
    };
    if (temFoto) {
        imagem.src = rota + "?v=" + Date.now();
    } else {
        imagem.removeAttribute("src");
    }
}

function converterArquivoParaUpload(arquivo) {
    if (!arquivo || !["image/png", "image/jpeg"].includes(arquivo.type)) {
        throw new Error("Escolha uma imagem PNG ou JPEG.");
    }
    if (arquivo.size > 2 * 1024 * 1024) {
        throw new Error("A imagem deve ter no máximo 2 MB.");
    }
    return new Promise(function(resolve, reject) {
        const reader = new FileReader();
        reader.onerror = function() {
            reject(new Error("Não foi possível ler o arquivo selecionado."));
        };
        reader.onload = function() {
            const resultado = String(reader.result || "");
            const separador = resultado.indexOf(",");
            if (separador < 0) {
                reject(new Error("O arquivo de imagem é inválido."));
                return;
            }
            resolve({
                tipoMime: arquivo.type,
                base64: resultado.slice(separador + 1)
            });
        };
        reader.readAsDataURL(arquivo);
    });
}

async function enviarFoto(rota, arquivo) {
    const imagem = await converterArquivoParaUpload(arquivo);
    return requisitar(rota, {
        method: "PUT",
        body: JSON.stringify(imagem)
    });
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
    } catch (erro) {
        throw new Error("Não foi possível conectar ao backend.");
    }
    const contentType = response.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) {
        throw new Error("O servidor respondeu em um formato inesperado.");
    }
    const data = await response.json();
    if (response.status === 401) {
        window.location.replace("/login");
        throw new Error("Sua sessão expirou. Entre novamente.");
    }
    if (!response.ok) {
        throw new Error(data.erro || "Não foi possível concluir a solicitação.");
    }
    return data;
}

function mostrarPagina(id) {
    const page = document.getElementById(id);
    if (!page || !page.classList.contains("client-page")) return;
    document.querySelectorAll(".client-page").forEach(function(item) {
        item.classList.toggle("escondido", item !== page);
    });
    document.querySelectorAll("[data-client-page]").forEach(function(button) {
        const selected = button.dataset.clientPage === id;
        button.classList.toggle("ativo", selected);
        if (selected) button.setAttribute("aria-current", "page");
        else button.removeAttribute("aria-current");
    });
}

function criarOpcao(valor, texto) {
    const option = document.createElement("option");
    option.value = valor;
    option.textContent = texto;
    return option;
}

function atualizarSelecaoPets() {
    const select = document.getElementById("agendamento-pet");
    select.replaceChildren(criarOpcao("", pets.length ? "Selecione seu pet" : "Cadastre um pet primeiro"));
    pets.forEach(function(pet) {
        select.append(criarOpcao(pet.id, pet.nome));
    });
    select.disabled = pets.length === 0;
}

function criarLinhaPet(pet) {
    const row = document.createElement("li");
    row.className = "registro";
    const identidade = document.createElement("div");
    identidade.className = "pet-identidade";
    const avatar = document.createElement("div");
    avatar.className = "pet-avatar";
    const fallback = document.createElement("span");
    fallback.textContent = "♡";
    const imagem = document.createElement("img");
    imagem.alt = "Foto de " + pet.nome;
    imagem.hidden = true;
    avatar.append(fallback, imagem);
    atualizarImagem(imagem, fallback, pet.temFoto, "/api/pets/" + pet.id + "/foto");

    const info = document.createElement("div");
    const name = document.createElement("strong");
    const details = document.createElement("span");
    name.textContent = pet.nome;
    details.textContent = pet.especie + " · " + (pet.raca || "Raça não informada");
    info.append(name, details);
    identidade.append(avatar, info);

    const actions = document.createElement("div");
    actions.className = "registro-acoes";
    const photoButton = document.createElement("button");
    photoButton.type = "button";
    photoButton.className = "editar";
    photoButton.textContent = pet.temFoto ? "Alterar foto" : "Adicionar foto";
    const fileInput = document.createElement("input");
    fileInput.type = "file";
    fileInput.accept = "image/png,image/jpeg";
    fileInput.hidden = true;
    fileInput.setAttribute("aria-label", "Selecionar foto de " + pet.nome);
    photoButton.addEventListener("click", function() {
        fileInput.click();
    });
    fileInput.addEventListener("change", async function() {
        if (!fileInput.files || !fileInput.files[0]) return;
        photoButton.disabled = true;
        try {
            await enviarFoto("/api/pets/" + pet.id + "/foto", fileInput.files[0]);
            await carregarDados();
        } catch (erro) {
            window.alert(mensagemDeErro(erro));
        } finally {
            photoButton.disabled = false;
            fileInput.value = "";
        }
    });

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "remover";
    removeButton.textContent = "Remover pet";
    removeButton.addEventListener("click", async function() {
        if (!window.confirm("Deseja remover " + pet.nome + " da sua conta?")) return;
        removeButton.disabled = true;
        try {
            await requisitar("/api/pets/" + pet.id, { method: "DELETE" });
            await carregarDados();
        } catch (erro) {
            window.alert(mensagemDeErro(erro));
        } finally {
            removeButton.disabled = false;
        }
    });
    actions.append(photoButton, fileInput, removeButton);
    row.append(identidade, actions);
    return row;
}

function criarLinhaAgendamento(appointment) {
    const row = document.createElement("li");
    row.className = "registro";
    const info = document.createElement("div");
    const name = document.createElement("strong");
    const details = document.createElement("span");
    name.textContent = appointment.pet;
    const date = new Intl.DateTimeFormat("pt-BR").format(
        new Date(appointment.data + "T00:00:00")
    );
    details.textContent = date + " às " + appointment.horario + " · "
        + appointment.motivo + " · " + appointment.status;
    info.append(name, details);
    row.append(info);

    if (appointment.status !== "CANCELADO" && appointment.status !== "CONCLUIDO") {
        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "remover";
        cancel.textContent = "Solicitar cancelamento";
        cancel.addEventListener("click", async function() {
            if (!window.confirm("Deseja solicitar o cancelamento deste agendamento?")) return;
            try {
                await requisitar("/api/agendamentos/" + appointment.id, { method: "DELETE" });
                await carregarDados();
            } catch (erro) {
                window.alert(mensagemDeErro(erro));
            }
        });
        row.append(cancel);
    }
    return row;
}

function renderizarLista(elementId, records, createRow) {
    const list = document.getElementById(elementId);
    list.replaceChildren();
    if (records.length === 0) {
        const empty = document.createElement("li");
        empty.className = "lista-vazia";
        empty.textContent = "Nenhum registro cadastrado.";
        list.append(empty);
        return;
    }
    records.forEach(function(record) {
        list.append(createRow(record));
    });
}

async function carregarDados() {
    const [profile, petList, appointmentList] = await Promise.all([
        requisitar("/api/cliente/me", { method: "GET" }),
        requisitar("/api/pets", { method: "GET" }),
        requisitar("/api/agendamentos", { method: "GET" })
    ]);
    pets = petList;
    agendamentos = appointmentList;
    document.getElementById("cliente-nome").value = profile.nome;
    document.getElementById("cliente-email").value = profile.email;
    document.getElementById("cliente-telefone").value = profile.telefone;
    document.getElementById("nome-cliente").textContent = profile.nome;
    document.getElementById("titulo-cliente").textContent = "Olá, " + profile.nome.split(" ")[0] + "!";
    document.getElementById("remover-foto-perfil").hidden = !profile.temFoto;
    atualizarImagem(
        document.getElementById("avatar-imagem-header"),
        document.getElementById("avatar-fallback-header"),
        profile.temFoto,
        "/api/cliente/me/foto"
    );
    atualizarImagem(
        document.getElementById("avatar-imagem-conta"),
        document.getElementById("avatar-fallback-conta"),
        profile.temFoto,
        "/api/cliente/me/foto"
    );
    renderizarLista("lista-pets", pets, criarLinhaPet);
    renderizarLista("lista-agendamentos", agendamentos, criarLinhaAgendamento);
    atualizarSelecaoPets();
    mostrarMensagem(document.getElementById("api-status"), "Conectado à sua conta.", false);
}

function configurarFormulario(formId, rota, messageId, method, prepararDados) {
    const form = document.getElementById(formId);
    form.addEventListener("submit", async function(event) {
        event.preventDefault();
        const button = form.querySelector('button[type="submit"]');
        const message = document.getElementById(messageId);
        button.disabled = true;
        mostrarMensagem(message, "Salvando...", false);
        try {
            const fields = Object.fromEntries(new FormData(form).entries());
            await requisitar(rota, {
                method: method,
                body: JSON.stringify(prepararDados(fields))
            });
            form.reset();
            await carregarDados();
            mostrarMensagem(message, "Dados salvos com sucesso.", false);
        } catch (erro) {
            mostrarMensagem(message, mensagemDeErro(erro), true);
        } finally {
            button.disabled = false;
        }
    });
}

document.addEventListener("DOMContentLoaded", async function() {
    document.querySelectorAll("[data-client-page]").forEach(function(button) {
        button.addEventListener("click", function(event) {
            event.preventDefault();
            mostrarPagina(button.dataset.clientPage);
        });
    });
    document.getElementById("sair").addEventListener("click", async function() {
        try {
            await requisitar("/api/auth/logout", { method: "POST" });
        } catch (erro) {
            window.alert(mensagemDeErro(erro));
        } finally {
            window.location.replace("/login");
        }
    });

    configurarFormulario("form-perfil", "/api/cliente/me", "mensagem-perfil", "PUT",
        function(fields) { return fields; });
    const photoForm = document.getElementById("form-foto-perfil");
    photoForm.addEventListener("submit", async function(event) {
        event.preventDefault();
        const button = photoForm.querySelector('button[type="submit"]');
        const message = document.getElementById("mensagem-foto-perfil");
        const input = document.getElementById("arquivo-foto-perfil");
        button.disabled = true;
        mostrarMensagem(message, "Enviando foto...", false);
        try {
            await enviarFoto("/api/cliente/me/foto", input.files[0]);
            photoForm.reset();
            await carregarDados();
            mostrarMensagem(message, "Foto de perfil atualizada.", false);
        } catch (erro) {
            mostrarMensagem(message, mensagemDeErro(erro), true);
        } finally {
            button.disabled = false;
        }
    });
    document.getElementById("remover-foto-perfil").addEventListener("click", async function() {
        if (!window.confirm("Deseja remover sua foto de perfil?")) return;
        try {
            await requisitar("/api/cliente/me/foto", { method: "DELETE" });
            await carregarDados();
            mostrarMensagem(
                document.getElementById("mensagem-foto-perfil"),
                "Foto de perfil removida.",
                false
            );
        } catch (erro) {
            mostrarMensagem(
                document.getElementById("mensagem-foto-perfil"),
                mensagemDeErro(erro),
                true
            );
        }
    });
    configurarFormulario("form-pet", "/api/pets", "mensagem-pet", "POST",
        function(fields) { return fields; });
    configurarFormulario("form-agendamento", "/api/agendamentos", "mensagem-agendamento", "POST",
        function(fields) { return { ...fields, petId: Number(fields.petId) }; });

    const currentDate = new Date();
    document.getElementById("agendamento-data").min = [
        currentDate.getFullYear(),
        String(currentDate.getMonth() + 1).padStart(2, "0"),
        String(currentDate.getDate()).padStart(2, "0")
    ].join("-");

    try {
        const session = await requisitar("/api/auth/me", { method: "GET" });
        if (session.usuario.perfil !== "CLIENTE") {
            window.location.replace("/funcionario");
            return;
        }
        csrfToken = session.csrfToken;
        await carregarDados();
    } catch (erro) {
        mostrarMensagem(document.getElementById("api-status"), mensagemDeErro(erro), true);
    }
});
