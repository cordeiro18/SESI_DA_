# Acesso ao sistema DogMania

## Inicializar com duplo clique

Execute `Iniciar DogMania.cmd` na pasta principal do projeto. O inicializador verifica se a API já está ativa; se não estiver, inicia o backend e aguarda a conexão antes de abrir a tela de login no navegador.

## Iniciar o site

Use a tarefa existente do VS Code **Clínica Veterinária: iniciar site e API** ou execute `iniciar.cmd` nesta pasta. O site fica disponível em `http://127.0.0.1:8081`.

O primeiro início aplica migrações aditivas: adiciona `usuarios.tutor_id`, campos de convite em `tutores` e colunas para fotos de perfil e de pets. Os registros existentes não são apagados nem recriados; as imagens são guardadas no próprio SQLite.

## Senhas dos funcionários existentes

As senhas atualmente armazenadas no banco não são hashes BCrypt válidos. Para definir uma senha nova para uma conta de funcionário existente, abra um terminal interativo nesta pasta e execute:

```powershell
.\redefinir-senha.cmd
```

Informe o e-mail da conta e digite a senha duas vezes. A senha não aparece no terminal e somente o hash BCrypt é salvo. Use pelo menos 12 caracteres. Não execute esse comando em terminais que gravem a entrada digitada.

## Contas de cliente

Para um tutor novo, escolha **Criar conta de cliente** na tela de login; o perfil não pode ser escolhido no navegador e o CPF é armazenado cifrado.

Para um tutor já cadastrado, um funcionário deve confirmar a identidade presencialmente, abrir **Tutores** e gerar um convite. Entregue o código somente ao titular por um canal privado. O cliente escolhe **Tenho um convite** na tela de login e informa o código, o e-mail e uma senha de pelo menos 12 caracteres. O convite expira em 24 horas, só pode ser usado uma vez e o banco armazena apenas seu hash. Gerar um convite novo invalida o anterior.

## Proteções

- As rotas verificam sessão, perfil e vínculo com o tutor no backend.
- A sessão usa cookie `HttpOnly` e `SameSite=Strict`; operações que alteram dados também exigem token CSRF.
- O servidor escuta somente em `127.0.0.1`. Mantenha-o local; não o exponha diretamente à rede ou à internet sem HTTPS e uma configuração de produção apropriada.
- Clientes veem apenas pets e agendamentos vinculados ao tutor da sessão. O backend ignora IDs de tutor enviados por contas de cliente.
- Clientes podem remover seus próprios pets quando não há agendamentos vinculados; o histórico impede a exclusão definitiva.
- Fotos de perfil e dos pets aceitam PNG/JPEG de até 2 MB, são validadas no backend e só podem ser alteradas pelo titular ou por um funcionário autenticado.
