let tutores = [];
let pets = [];
let agendamentos = [];

function mostrarPagina(paginaSelecionada) {
    const pagina = document.getElementById(paginaSelecionada);
    if (!pagina || !pagina.classList.contains("pagina")) return;

    document.querySelectorAll(".pagina").forEach(function(item) {
        item.classList.toggle("escondido", item !== pagina);
    });

    document.querySelectorAll("[data-pagina]").forEach(function(botao) {
        const selecionado = botao.dataset.pagina === paginaSelecionada;
        botao.classList.toggle("ativo", selecionado);
        if (selecionado) {
            botao.setAttribute("aria-current", "page");
        } else {
            botao.removeAttribute("aria-current");
        }
    });
}

async function requisitar(url, opcoes) {
    const resposta = await fetch(url, opcoes);
    const dados = await resposta.json();
    if (!resposta.ok) {
        throw new Error(dados.erro || "A solicitação não foi concluída.");
    }
    return dados;
}

function exibirMensagem(elemento, texto, erro) {
    elemento.textContent = texto;
    elemento.classList.toggle("erro", Boolean(erro));
}

function mensagemDeErro(erro) {
    if (erro instanceof TypeError) {
        return "Não foi possível conectar ao backend. Inicie o site pelo comando Maven e tente novamente.";
    }
    return erro.message || "Ocorreu um erro inesperado.";
}

function criarOpcao(valor, texto) {
    const opcao = document.createElement("option");
    opcao.value = valor;
    opcao.textContent = texto;
    return opcao;
}

function atualizarSelecao(elemento, registros, textoVazio, formatar) {
    elemento.replaceChildren(criarOpcao("", textoVazio));
    registros.forEach(function(registro) {
        elemento.append(criarOpcao(registro.id, formatar(registro)));
    });
    elemento.disabled = registros.length === 0;
}

function criarLinha(tipo, registro, rota) {
    const linha = document.createElement("li");
    linha.className = "registro";

    const informacoes = document.createElement("div");
    const nome = document.createElement("strong");
    const detalhes = document.createElement("span");
    const botaoRemover = document.createElement("button");

    if (tipo === "tutores") {
        nome.textContent = registro.nome;
        detalhes.textContent = "Telefone: " + registro.telefone + " · CPF protegido no banco";
    } else if (tipo === "pets") {
        nome.textContent = registro.nome;
        detalhes.textContent = registro.especie + " · " + (registro.raca || "Raça não informada")
            + " · Tutor: " + registro.tutorNome;
    } else {
        nome.textContent = registro.pet;
        const data = new Intl.DateTimeFormat("pt-BR").format(new Date(registro.data + "T00:00:00"));
        detalhes.textContent = data + " às " + registro.horario + " · " + registro.motivo + " · " + registro.status;
    }

    botaoRemover.type = "button";
    botaoRemover.className = "remover";
    botaoRemover.textContent = "Remover";
    botaoRemover.addEventListener("click", async function() {
        if (!window.confirm("Deseja remover este registro?")) return;
        try {
            await requisitar(rota + "/" + registro.id, { method: "DELETE" });
            await carregarDados();
        } catch (erro) {
            window.alert(mensagemDeErro(erro));
        }
    });

    informacoes.append(nome, detalhes);
    linha.append(informacoes, botaoRemover);
    return linha;
}

function renderizarLista(tipo, registros, rota) {
    const lista = document.getElementById("lista-" + tipo);
    lista.replaceChildren();

    if (registros.length === 0) {
        const vazio = document.createElement("li");
        vazio.className = "lista-vazia";
        vazio.textContent = "Nenhum registro cadastrado.";
        lista.append(vazio);
    } else {
        registros.forEach(function(registro) {
            lista.append(criarLinha(tipo, registro, rota));
        });
    }

    document.getElementById("total-" + tipo).textContent = registros.length;
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
    const resultados = await Promise.all([
        requisitar("/api/tutores"),
        requisitar("/api/pets"),
        requisitar("/api/agendamentos")
    ]);
    [tutores, pets, agendamentos] = resultados;
    atualizarTela();
    exibirMensagem(status, "Conectado ao banco de dados.", false);
}

function cadastrar(formulario, rota, mensagemId, prepararDados) {
    formulario.addEventListener("submit", async function(evento) {
        evento.preventDefault();
        const botao = formulario.querySelector('button[type="submit"]');
        const mensagem = document.getElementById(mensagemId);
        botao.disabled = true;
        exibirMensagem(mensagem, "Salvando no banco de dados...", false);

        try {
            const campos = Object.fromEntries(new FormData(formulario).entries());
            await requisitar(rota, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(prepararDados(campos))
            });
            formulario.reset();
            await carregarDados();
            exibirMensagem(mensagem, "Cadastro salvo no banco de dados.", false);
        } catch (erro) {
            exibirMensagem(mensagem, mensagemDeErro(erro), true);
        } finally {
            botao.disabled = false;
        }
    });
}

document.addEventListener("DOMContentLoaded", function() {
    cadastrar(
        document.getElementById("form-tutor"),
        "/api/tutores",
        "mensagem-tutor",
        function(campos) { return campos; }
    );
    cadastrar(
        document.getElementById("form-pet"),
        "/api/pets",
        "mensagem-pet",
        function(campos) {
            return { ...campos, tutorId: Number(campos.tutorId) };
        }
    );
    cadastrar(
        document.getElementById("form-agendamento"),
        "/api/agendamentos",
        "mensagem-agendamento",
        function(campos) {
            return { ...campos, petId: Number(campos.petId), horario: campos.hora };
        }
    );

    const dataAtual = new Date();
    const campoData = document.getElementById("agendamento-data");
    campoData.min = [
        dataAtual.getFullYear(),
        String(dataAtual.getMonth() + 1).padStart(2, "0"),
        String(dataAtual.getDate()).padStart(2, "0")
    ].join("-");

    carregarDados().catch(function(erro) {
        exibirMensagem(
            document.getElementById("api-status"),
            mensagemDeErro(erro),
            true
        );
    });
});
