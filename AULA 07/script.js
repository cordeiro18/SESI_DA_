function calcularOrcamento() {

    let nome = document.getElementById("nome").value;

    let convidados = parseInt(
        document.getElementById("convidados").value
    );

    let animadores = parseInt(
        document.getElementById("animadores").value
    );

    let tipoFesta = document.getElementById("tipoFesta").value;

   
    if(nome === "" || isNaN(convidados) || isNaN(animadores)) {

        alert("Preencha todos os campos!");

        return;
    }

    let valorTipo = 0;

    if(tipoFesta === "Aniversário") {
        valorTipo = 800;
    }

    else if(tipoFesta === "Casamento") {
        valorTipo = 3000;
    }

    else if(tipoFesta === "Infantil") {
        valorTipo = 1200;
    }

    else if(tipoFesta === "Empresa") {
        valorTipo = 2500;
    }

    let valorConvidados = convidados * 18;

    

    let valorAnimadores = animadores * 350;

 
    let taxaExtra = 0;

    if(convidados > 150) {
        taxaExtra = 1000;
    }

    

    let desconto = 0;

    if(convidados < 50) {
        desconto = 200;
    }


    let total =
        valorTipo +
        valorConvidados +
        valorAnimadores +
        taxaExtra -
        desconto;

  

    document.getElementById("mensagem").innerHTML = `

        <strong>Cliente:</strong> ${nome}<br><br>

        <strong>Tipo da Festa:</strong> ${tipoFesta}<br><br>

        <strong>Quantidade de convidados:</strong> ${convidados}<br><br>

        <strong>Quantidade de animadores:</strong> ${animadores}<br><br>

        <hr><br>

        <strong>Valor da festa:</strong> R$ ${valorTipo.toFixed(2)}<br><br>

        <strong>Custo convidados:</strong> R$ ${valorConvidados.toFixed(2)}<br><br>

        <strong>Custo animadores:</strong> R$ ${valorAnimadores.toFixed(2)}<br><br>

        <strong>Taxa extra:</strong> R$ ${taxaExtra.toFixed(2)}<br><br>

        <strong>Desconto:</strong> R$ ${desconto.toFixed(2)}<br><br>

        <hr><br>

        <span style="
            color: green;
            font-size: 32px;
            font-weight: bold;
        ">
            TOTAL: R$ ${total.toFixed(2)}
        </span>
    `;
}