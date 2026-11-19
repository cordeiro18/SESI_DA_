package br.com.clinicaveterinaria;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Conexao {
    private static final String NOME_BANCO = "clinica.db";
    private static final String[] NOMES_DIRETORIO = {"database", "Database"};

    private Conexao() {
    }

    public static Connection abrir() throws SQLException {
        Path caminhoBanco = localizarBanco();
        Connection conexao = DriverManager.getConnection(
                "jdbc:sqlite:" + caminhoBanco.toAbsolutePath().normalize());

        try (Statement comando = conexao.createStatement()) {
            comando.execute("PRAGMA foreign_keys = ON");
            return conexao;
        } catch (SQLException erro) {
            try {
                conexao.close();
            } catch (SQLException erroAoFechar) {
                erro.addSuppressed(erroAoFechar);
            }
            throw erro;
        }
    }

    public static Path localizarBanco() throws SQLException {
        String caminhoConfigurado = System.getProperty("clinica.db.path");
        if (caminhoConfigurado != null && !caminhoConfigurado.isBlank()) {
            Path caminho = Path.of(caminhoConfigurado).toAbsolutePath().normalize();
            if (Files.isRegularFile(caminho)) {
                return caminho;
            }
            throw new SQLException("O banco configurado não existe: " + caminho);
        }

        Path diretorio = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (diretorio != null) {
            for (String nomeDiretorio : NOMES_DIRETORIO) {
                Path candidato = diretorio.resolve(nomeDiretorio).resolve(NOME_BANCO);
                if (Files.isRegularFile(candidato)) {
                    return candidato;
                }
            }
            diretorio = diretorio.getParent();
        }

        throw new SQLException(
                "Não foi encontrado database/clinica.db ou Database/clinica.db "
                        + "na pasta atual ou em suas pastas superiores. "
                        + "Também é possível configurar -Dclinica.db.path=<caminho>.");
    }
}
