package br.com.clinicaveterinaria;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseMigration {
    private DatabaseMigration() {
    }

    public static void aplicar() throws SQLException {
        try (Connection connection = Conexao.abrir()) {
            connection.setAutoCommit(false);
            try {
                if (!possuiColunaTutorId(connection)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("""
                                ALTER TABLE usuarios
                                ADD COLUMN tutor_id INTEGER
                                REFERENCES tutores(id)
                                ON UPDATE CASCADE
                                ON DELETE RESTRICT
                                """);
                    }
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute("""
                            CREATE UNIQUE INDEX IF NOT EXISTS idx_usuarios_tutor_id
                            ON usuarios(tutor_id)
                            WHERE tutor_id IS NOT NULL
                            """);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static boolean possuiColunaTutorId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(usuarios)")) {
            while (result.next()) {
                if ("tutor_id".equalsIgnoreCase(result.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
