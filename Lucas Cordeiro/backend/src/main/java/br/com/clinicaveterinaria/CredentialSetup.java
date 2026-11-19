package br.com.clinicaveterinaria;

import java.io.Console;
import java.sql.SQLException;
import java.util.Arrays;

public final class CredentialSetup {
    private CredentialSetup() {
    }

    public static void executar() throws SQLException {
        DatabaseMigration.aplicar();
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException(
                    "Execute este comando em um terminal interativo para digitar a senha sem exibi-la.");
        }

        String email = console.readLine("E-mail da conta de funcionário: ");
        char[] password = console.readPassword("Nova senha (mínimo 12 caracteres): ");
        char[] confirmation = console.readPassword("Confirme a nova senha: ");
        try {
            if (password == null || confirmation == null) {
                throw new IllegalStateException("Não foi possível ler a senha informada.");
            }
            if (!Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("As senhas não coincidem.");
            }
            AuthService.redefinirSenhaFuncionario(email, password);
            console.printf("Senha atualizada com hash BCrypt.%n");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
