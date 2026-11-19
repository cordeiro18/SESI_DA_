package br.com.clinicaveterinaria;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws SQLException {
        try (Connection conexao = Conexao.abrir();
             Statement comando = conexao.createStatement()) {
            boolean chavesEstrangeirasAtivas;
            try (ResultSet resultado = comando.executeQuery("PRAGMA foreign_keys")) {
                chavesEstrangeirasAtivas = resultado.next() && resultado.getInt(1) == 1;
            }

            try (ResultSet resultado = comando.executeQuery("SELECT COUNT(*) FROM tutores")) {
                resultado.next();
                System.out.println("Conexão SQLite estabelecida.");
                System.out.println("Chaves estrangeiras: "
                        + (chavesEstrangeirasAtivas ? "ativadas" : "desativadas"));
                System.out.println("Tutores cadastrados: " + resultado.getLong(1));
                System.out.println("Banco utilizado: " + Conexao.localizarBanco());
            }
        }
    }
}
