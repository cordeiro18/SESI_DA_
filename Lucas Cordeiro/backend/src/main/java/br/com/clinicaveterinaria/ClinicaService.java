package br.com.clinicaveterinaria;

import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class ClinicaService {
    public record Tutor(long id, String nome, String telefone) {
    }

    public record Pet(long id, long tutorId, String tutorNome, String nome, String especie, String raca) {
    }

    public record Agendamento(
            long id, long petId, String pet, String data, String horario, String motivo, String status) {
    }

    public record TutorRequest(String nome, String cpf, String telefone) {
    }

    public record PetRequest(long tutorId, String nome, String especie, String raca) {
    }

    public record AgendamentoRequest(long petId, String data, String horario, String motivo) {
    }

    public long contarTutores() throws SQLException {
        try (Connection connection = Conexao.abrir();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM tutores")) {
            result.next();
            return result.getLong(1);
        }
    }

    public List<Tutor> listarTutores() throws SQLException {
        List<Tutor> tutors = new ArrayList<>();
        String sql = "SELECT id, nome, telefone FROM tutores ORDER BY nome";
        try (Connection connection = Conexao.abrir();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                tutors.add(new Tutor(
                        result.getLong("id"),
                        result.getString("nome"),
                        result.getString("telefone")));
            }
        }
        return tutors;
    }

    public Tutor criarTutor(TutorRequest request)
            throws SQLException, GeneralSecurityException {
        String nome = requireText(request.nome(), "Informe o nome do tutor.");
        String telefone = requireText(request.telefone(), "Informe o telefone do tutor.");
        String cpf = CpfCriptografia.normalizar(request.cpf());
        String cpfCifrado = CpfCriptografia.criptografar(cpf);

        try (Connection connection = Conexao.abrir()) {
            if (cpfJaExiste(connection, cpf)) {
                throw new CpfDuplicadoException();
            }

            String sql = "INSERT INTO tutores (nome, cpf_cifrado, telefone) VALUES (?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nome);
                statement.setString(2, cpfCifrado);
                statement.setString(3, telefone);
                statement.executeUpdate();
            }
            return new Tutor(lastInsertedId(connection), nome, telefone);
        }
    }

    public List<Pet> listarPets() throws SQLException {
        List<Pet> pets = new ArrayList<>();
        String sql = """
                SELECT p.id, p.tutor_id, t.nome AS tutor_nome, p.nome, p.especie, p.raca
                FROM pets p
                JOIN tutores t ON t.id = p.tutor_id
                ORDER BY p.nome
                """;
        try (Connection connection = Conexao.abrir();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                pets.add(new Pet(
                        result.getLong("id"),
                        result.getLong("tutor_id"),
                        result.getString("tutor_nome"),
                        result.getString("nome"),
                        result.getString("especie"),
                        result.getString("raca")));
            }
        }
        return pets;
    }

    public Pet criarPet(PetRequest request) throws SQLException {
        if (request.tutorId() <= 0) {
            throw new IllegalArgumentException("Selecione um tutor para o pet.");
        }
        String nome = requireText(request.nome(), "Informe o nome do pet.");
        String especie = requireText(request.especie(), "Informe a espécie do pet.");
        String raca = blankToNull(request.raca());

        String sql = "INSERT INTO pets (tutor_id, nome, especie, raca) VALUES (?, ?, ?, ?)";
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, request.tutorId());
            statement.setString(2, nome);
            statement.setString(3, especie);
            statement.setString(4, raca);
            statement.executeUpdate();
            long id = lastInsertedId(connection);
            return buscarPet(connection, id);
        }
    }

    public List<Agendamento> listarAgendamentos() throws SQLException {
        List<Agendamento> appointments = new ArrayList<>();
        String sql = """
                SELECT a.id, a.pet_id, p.nome AS pet, a.data, a.horario, a.motivo, a.status
                FROM agendamentos a
                JOIN pets p ON p.id = a.pet_id
                ORDER BY a.data, a.horario
                """;
        try (Connection connection = Conexao.abrir();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                appointments.add(new Agendamento(
                        result.getLong("id"),
                        result.getLong("pet_id"),
                        result.getString("pet"),
                        result.getString("data"),
                        result.getString("horario"),
                        result.getString("motivo"),
                        result.getString("status")));
            }
        }
        return appointments;
    }

    public Agendamento criarAgendamento(AgendamentoRequest request) throws SQLException {
        if (request.petId() <= 0) {
            throw new IllegalArgumentException("Selecione um pet para o agendamento.");
        }
        String data = validarData(request.data());
        String horario = validarHorario(request.horario());
        String motivo = requireText(request.motivo(), "Informe o motivo do atendimento.");

        String sql = "INSERT INTO agendamentos (pet_id, data, horario, motivo) VALUES (?, ?, ?, ?)";
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, request.petId());
            statement.setString(2, data);
            statement.setString(3, horario);
            statement.setString(4, motivo);
            statement.executeUpdate();
            return buscarAgendamento(connection, lastInsertedId(connection));
        }
    }

    public boolean removerTutor(long id) throws SQLException {
        return remover("tutores", id);
    }

    public boolean removerPet(long id) throws SQLException {
        return remover("pets", id);
    }

    public boolean removerAgendamento(long id) throws SQLException {
        return remover("agendamentos", id);
    }

    private boolean cpfJaExiste(Connection connection, String cpf)
            throws SQLException, GeneralSecurityException {
        String sql = "SELECT cpf_cifrado FROM tutores WHERE cpf_cifrado IS NOT NULL";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                if (CpfCriptografia.corresponde(result.getString(1), cpf)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Pet buscarPet(Connection connection, long id) throws SQLException {
        String sql = """
                SELECT p.id, p.tutor_id, t.nome AS tutor_nome, p.nome, p.especie, p.raca
                FROM pets p
                JOIN tutores t ON t.id = p.tutor_id
                WHERE p.id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new Pet(
                        result.getLong("id"),
                        result.getLong("tutor_id"),
                        result.getString("tutor_nome"),
                        result.getString("nome"),
                        result.getString("especie"),
                        result.getString("raca"));
            }
        }
    }

    private Agendamento buscarAgendamento(Connection connection, long id) throws SQLException {
        String sql = """
                SELECT a.id, a.pet_id, p.nome AS pet, a.data, a.horario, a.motivo, a.status
                FROM agendamentos a
                JOIN pets p ON p.id = a.pet_id
                WHERE a.id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new Agendamento(
                        result.getLong("id"),
                        result.getLong("pet_id"),
                        result.getString("pet"),
                        result.getString("data"),
                        result.getString("horario"),
                        result.getString("motivo"),
                        result.getString("status"));
            }
        }
    }

    private boolean remover(String tableName, long id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("O identificador do registro é inválido.");
        }
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    private long lastInsertedId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT last_insert_rowid()")) {
            result.next();
            return result.getLong(1);
        }
    }

    private String validarData(String value) {
        try {
            LocalDate date = LocalDate.parse(requireText(value, "Informe a data do atendimento."));
            if (date.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("A data do atendimento não pode estar no passado.");
            }
            return date.toString();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("A data do atendimento é inválida.", exception);
        }
    }

    private String validarHorario(String value) {
        try {
            return LocalTime.parse(requireText(value, "Informe o horário do atendimento.")).toString();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("O horário do atendimento é inválido.", exception);
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class CpfDuplicadoException extends RuntimeException {
        public CpfDuplicadoException() {
            super("Já existe um tutor cadastrado com este CPF.");
        }
    }
}
