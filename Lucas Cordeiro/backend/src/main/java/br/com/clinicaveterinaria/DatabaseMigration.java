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
                if (!possuiColunaConviteHash(connection)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE tutores ADD COLUMN convite_hash TEXT");
                    }
                }
                if (!possuiColunaConviteExpiracao(connection)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE tutores ADD COLUMN convite_expira_em INTEGER");
                    }
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute("""
                            CREATE UNIQUE INDEX IF NOT EXISTS idx_usuarios_tutor_id
                            ON usuarios(tutor_id)
                            WHERE tutor_id IS NOT NULL
                            """);
                    statement.execute("""
                            CREATE UNIQUE INDEX IF NOT EXISTS idx_tutores_convite_hash
                            ON tutores(convite_hash)
                            WHERE convite_hash IS NOT NULL
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
        return possuiColuna(connection, "usuarios", "tutor_id");
    }

    private static boolean possuiColunaConviteHash(Connection connection) throws SQLException {
        return possuiColuna(connection, "tutores", "convite_hash");
    }

    private static boolean possuiColunaConviteExpiracao(Connection connection) throws SQLException {
        return possuiColuna(connection, "tutores", "convite_expira_em");
    }

    private static boolean possuiColuna(Connection connection, String table, String column)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
