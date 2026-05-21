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

    else if(tipoFesta === "Baile Funk") {
        valorTipo = 2200;
    }

    else if(tipoFesta === "Rave Eletrônica") {
        valorTipo = 3500;
    }

    else if(tipoFesta === "Balada Gospel") {
        valorTipo = 1800;
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

    // Armazenar valor total para posterior uso
    localStorage.setItem('valorOrcamento', total.toFixed(2));
    localStorage.setItem('nomeCliente', nome);

    // Mostrar seção de pagamento
    document.getElementById('pagamento').style.display = 'block';
    document.getElementById('metodoPagamento').value = '';
    document.getElementById('detalhPagamento').innerHTML = '';
}

function atualizarPagamento() {
    let metodo = document.getElementById('metodoPagamento').value;
    let detalhDiv = document.getElementById('detalhPagamento');
    let valor = localStorage.getItem('valorOrcamento');

    if(metodo === '') {
        detalhDiv.innerHTML = '';
        return;
    }

    if(metodo === 'pix') {
        detalhDiv.innerHTML = `
            <div class="detalhe-pagamento">
                <p><strong>✓ PIX Instantâneo</strong></p>
                <p>Valor a pagar: <strong style="color: green;">R$ ${valor}</strong></p>
                <p style="font-size: 12px; color: #666; margin-top: 10px;">Um QR Code será exibido para você escanear com seu celular.</p>
            </div>
        `;
    }

    else if(metodo === 'boleto') {
        let codigoBarras = gerarCodigoBarras();
        detalhDiv.innerHTML = `
            <div class="detalhe-pagamento">
                <p><strong>✓ Boleto Bancário</strong></p>
                <p>Valor a pagar: <strong style="color: green;">R$ ${valor}</strong></p>
                <p><strong>Código de Barras:</strong><br>${codigoBarras}</p>
                <p style="font-size: 12px; color: #666; margin-top: 10px;">Você receberá o boleto por email. Pague em qualquer banco.</p>
            </div>
        `;
    }

    else if(metodo === 'cartao') {
        detalhDiv.innerHTML = `
            <div class="detalhe-pagamento">
                <p><strong>✓ Cartão de Crédito</strong></p>
                <p>Valor a pagar: <strong style="color: green;">R$ ${valor}</strong></p>
                <label style="margin-top: 10px; font-size: 12px;">Número do Cartão:</label>
                <input type="text" id="numeroCartao" placeholder="0000 0000 0000 0000" maxlength="19">
                <label style="margin-top: 10px; font-size: 12px;">Nome do Titular:</label>
                <input type="text" id="nomeCartao" placeholder="NOME COMPLETO">
                <label style="margin-top: 10px; font-size: 12px;">Validade:</label>
                <input type="text" id="validadeCartao" placeholder="MM/AA" maxlength="5">
                <label style="margin-top: 10px; font-size: 12px;">CVV:</label>
                <input type="text" id="cvvCartao" placeholder="000" maxlength="3">
            </div>
        `;
    }

    else if(metodo === 'dinheiro') {
        detalhDiv.innerHTML = `
            <div class="detalhe-pagamento">
                <p><strong>✓ Pagamento em Dinheiro</strong></p>
                <p>Valor a pagar: <strong style="color: green;">R$ ${valor}</strong></p>
                <p style="font-size: 12px; color: #666; margin-top: 10px;">O pagamento deverá ser realizado no dia do evento. Não se esqueça de levar a quantia exata se possível.</p>
            </div>
        `;
    }
}

function gerarCodigoBarras() {
    let codigo = '';
    for(let i = 0; i < 47; i++) {
        codigo += Math.floor(Math.random() * 10);
    }
    return codigo.match(/.{1,11}/g).join(' ');
}

function finalizarPagamento() {
    let metodo = document.getElementById('metodoPagamento').value;

    if(metodo === '') {
        alert('Selecione um método de pagamento!');
        return;
    }

    if(metodo === 'pix') {
        gerarQrCodePix();
        document.getElementById('modalPix').style.display = 'flex';
    }

    else if(metodo === 'boleto') {
        alert('✓ Boleto gerado com sucesso!\nVocê receberá por email.');
        confirmarPedido();
    }

    else if(metodo === 'cartao') {
        let numero = document.getElementById('numeroCartao').value;
        let nome = document.getElementById('nomeCartao').value;
        let validade = document.getElementById('validadeCartao').value;
        let cvv = document.getElementById('cvvCartao').value;

        if(numero === '' || nome === '' || validade === '' || cvv === '') {
            alert('Preencha todos os dados do cartão!');
            return;
        }

        alert('✓ Pagamento aprovado!\nSua festa foi confirmada.');
        confirmarPedido();
    }

    else if(metodo === 'dinheiro') {
        alert('✓ Pedido registrado!\nAguardaremos o pagamento no dia do evento.');
        confirmarPedido();
    }
}

function gerarQrCodePix() {
    let valor = localStorage.getItem('valorOrcamento');
    let nomeCliente = localStorage.getItem('nomeCliente');
    let chavePix = '12345678901';

    // Dados para gerar o código PIX (formato simplificado)
    let dadosPix = `00020126580014BR.GOV.BCB.PIX0136${chavePix}52040000530398654061${valor}5802BR5913LUCAS CORDEIRO6009SAO PAULO62410503***63041D3D`;

    // Limpar container anterior
    let container = document.getElementById('qrCodeContainer');
    container.innerHTML = '';

    // Gerar QR Code usando toDataURL
    QRCode.toDataURL(dadosPix, {
        width: 250,
        margin: 10,
        color: {
            dark: '#000000',
            light: '#ffffff'
        }
    }, function (error, url) {
        if (error) {
            console.error('Erro ao gerar QR Code:', error);
            container.innerHTML = '<p style="color: red;">Erro ao gerar QR Code. Tente novamente.</p>';
        } else {
            let img = document.createElement('img');
            img.src = url;
            img.style.border = '2px solid #2575fc';
            img.style.borderRadius = '8px';
            container.appendChild(img);
        }
    });

    document.getElementById('valorPix').textContent = `R$ ${valor}`;
}

function fecharModalPix() {
    document.getElementById('modalPix').style.display = 'none';
    confirmarPedido();
}

function confirmarPedido() {
    alert('✓ Pedido confirmado com sucesso!\nEntraremos em contato em breve.');
    // Resetar formulário
    document.getElementById('nome').value = '';
    document.getElementById('tipoFesta').value = 'Aniversário';
    document.getElementById('convidados').value = '';
    document.getElementById('animadores').value = '';
    document.getElementById('metodoPagamento').value = '';
    document.getElementById('detalhPagamento').innerHTML = '';
    document.getElementById('mensagem').innerHTML = 'Preencha os dados para calcular.';
    document.getElementById('pagamento').style.display = 'none';
}

// Máscara para cartão
document.addEventListener('DOMContentLoaded', function() {
    let numeroCartao = document.getElementById('numeroCartao');
    if(numeroCartao) {
        numeroCartao.addEventListener('input', function(e) {
            e.target.value = e.target.value.replace(/\s/g, '').replace(/(\d{4})/g, '$1 ').trim();
        });
    }
});