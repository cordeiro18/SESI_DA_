
const chaves = {
    tutores: "vetcare.tutores",
    pets: "vetcare.pets",
    agendamentos: "vetcare.agendamentos"
};

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

function carregarRegistros(tipo) {
    const dados = localStorage.getItem(chaves[tipo]);
    return dados ? JSON.parse(dados) : [];
}

function salvarRegistros(tipo, registros) {
    localStorage.setItem(chaves[tipo], JSON.stringify(registros));
}

function mostrarMensagem(tipo, texto, erro) {
    const mensagem = document.getElementById("mensagem-" + tipo);
    mensagem.textContent = texto;
    mensagem.classList.toggle("erro", Boolean(erro));
}

function tipoMensagem(tipo) {
    return {
        tutores: "tutor",
        pets: "pet",
        agendamentos: "agendamento"
    }[tipo];
}

function criarLinha(tipo, registro) {
    const linha = document.createElement("li");
    linha.className = "registro";

    const informacoes = document.createElement("div");
    const nome = document.createElement("strong");
    const detalhes = document.createElement("span");
    const botaoRemover = document.createElement("button");

    if (tipo === "tutores") {
        nome.textContent = registro.nome;
        detalhes.textContent = "CPF: " + registro.cpf + " · Telefone: " + registro.telefone;
    } else if (tipo === "pets") {
        nome.textContent = registro.nome;
        detalhes.textContent = registro.especie + " · " + registro.raca;
    } else {
        nome.textContent = registro.pet;
        detalhes.textContent = new Intl.DateTimeFormat("pt-BR").format(new Date(registro.data + "T00:00:00")) + " às " + registro.hora;
    }

    botaoRemover.type = "button";
    botaoRemover.className = "remover";
    botaoRemover.textContent = "Remover";
    botaoRemover.addEventListener("click", function() {
        const registros = carregarRegistros(tipo).filter(function(item) {
            return item.id !== registro.id;
        });
        salvarRegistros(tipo, registros);
        renderizarRegistros(tipo);
    });

    informacoes.append(nome, detalhes);
    linha.append(informacoes, botaoRemover);
    return linha;
}

function renderizarRegistros(tipo) {
    const lista = document.getElementById("lista-" + tipo);
    const registros = carregarRegistros(tipo);
    lista.replaceChildren();

    if (registros.length === 0) {
        const vazio = document.createElement("li");
        vazio.className = "lista-vazia";
        vazio.textContent = "Nenhum registro cadastrado.";
        lista.append(vazio);
    } else {
        registros.forEach(function(registro) {
            lista.append(criarLinha(tipo, registro));
        });
    }

    document.getElementById("total-" + tipo).textContent = registros.length;
}

function cadastrar(tipo, formulario) {
    formulario.addEventListener("submit", function(evento) {
        evento.preventDefault();
        const campos = Object.fromEntries(new FormData(formulario).entries());
        const registros = carregarRegistros(tipo);

        registros.push({
            ...campos,
            id: Date.now().toString() + Math.random().toString(16).slice(2)
        });

        salvarRegistros(tipo, registros);
        formulario.reset();
        mostrarMensagem(tipoMensagem(tipo), "Cadastro realizado com sucesso.", false);
        renderizarRegistros(tipo);
    });
}

document.addEventListener("DOMContentLoaded", function() {
    cadastrar("tutores", document.getElementById("form-tutor"));
    cadastrar("pets", document.getElementById("form-pet"));
    cadastrar("agendamentos", document.getElementById("form-agendamento"));

    ["tutores", "pets", "agendamentos"].forEach(renderizarRegistros);

    const dataAtual = new Date();
    const campoData = document.getElementById("agendamento-data");
    campoData.min = [
        dataAtual.getFullYear(),
        String(dataAtual.getMonth() + 1).padStart(2, "0"),
        String(dataAtual.getDate()).padStart(2, "0")
    ].join("-");
});
