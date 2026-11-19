let csrfToken = "";

function mostrarMensagem(elemento, texto, erro) {
    elemento.textContent = texto;
    elemento.classList.toggle("erro", Boolean(erro));
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
        throw new Error("Não foi possível conectar ao site. Inicie o backend pelo VS Code.");
    }
    const contentType = response.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) {
        throw new Error("O servidor respondeu em um formato inesperado. Tente novamente.");
    }
    const data = await response.json();
    if (!response.ok) {
        throw new Error(data.erro || "Não foi possível concluir a solicitação.");
    }
    return data;
}

function irParaPainel(usuario) {
    window.location.replace(usuario.perfil === "CLIENTE" ? "/cliente" : "/funcionario");
}

function alternarFormulario(cadastro) {
    document.getElementById("form-login").classList.toggle("escondido", cadastro);
    document.getElementById("form-cadastro-cliente").classList.toggle("escondido", !cadastro);
    document.getElementById("texto-alternar").textContent =
        cadastro ? "Já tem uma conta?" : "Ainda não tem uma conta?";
    document.getElementById("alternar-formulario").textContent =
        cadastro ? "Entrar" : "Criar conta de cliente";
    document.getElementById("login-title").textContent =
        cadastro ? "Crie sua conta" : "Acesse sua conta";
}

function enviarFormulario(formulario, rota, mensagemId, prepararDados) {
    formulario.addEventListener("submit", async function(event) {
        event.preventDefault();
        const button = formulario.querySelector('button[type="submit"]');
        const message = document.getElementById(mensagemId);
        const defaultLabel = button.textContent;
        button.disabled = true;
        mostrarMensagem(message, "Aguarde...", false);

        try {
            const fields = Object.fromEntries(new FormData(formulario).entries());
            const data = await requisitar(rota, {
                method: "POST",
                body: JSON.stringify(prepararDados(fields))
            });
            csrfToken = data.csrfToken;
            irParaPainel(data.usuario);
        } catch (erro) {
            mostrarMensagem(message, erro.message, true);
        } finally {
            button.disabled = false;
            button.textContent = defaultLabel;
        }
    });
}

document.addEventListener("DOMContentLoaded", function() {
    document.getElementById("alternar-formulario").addEventListener("click", function() {
        const showRegistration = document.getElementById("form-login").classList.contains("escondido");
        alternarFormulario(!showRegistration);
    });

    document.querySelectorAll("[data-password-toggle]").forEach(function(button) {
        button.addEventListener("click", function() {
            const input = document.getElementById(button.dataset.passwordToggle);
            const showPassword = input.type === "password";
            input.type = showPassword ? "text" : "password";
            button.textContent = showPassword ? "Ocultar" : "Mostrar";
            button.setAttribute("aria-label", showPassword ? "Ocultar senha" : "Mostrar senha");
        });
    });

    enviarFormulario(
        document.getElementById("form-login"),
        "/api/auth/login",
        "mensagem-login",
        function(fields) { return fields; }
    );
    enviarFormulario(
        document.getElementById("form-cadastro-cliente"),
        "/api/auth/register",
        "mensagem-cadastro",
        function(fields) { return fields; }
    );

});
