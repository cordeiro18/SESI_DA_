package br.com.clinicaveterinaria;

import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClinicaService {
    public record Tutor(long id, String nome, String telefone) {
    }

    public record Pet(
            long id, long tutorId, String tutorNome, String nome, String especie, String raca, boolean temFoto) {
    }

    public record Agendamento(
            long id, long petId, String pet, String data, String horario, String motivo, String status) {
    }

    public record TutorRequest(String nome, String cpf, String telefone) {
    }

    public record TutorUpdateRequest(String nome, String telefone) {
    }

    public record PetRequest(Long tutorId, String nome, String especie, String raca) {
    }

    public record AgendamentoRequest(Long petId, String data, String horario, String motivo) {
    }

    public record AgendamentoUpdateRequest(
            Long petId, String data, String horario, String motivo, String status) {
    }

    public record ClientProfile(String nome, String email, String telefone, boolean temFoto) {
    }

    public record ClientProfileRequest(String nome, String email, String telefone) {
    }

    public record Foto(byte[] bytes, String tipoMime) {
    }

    public record ClientInvitation(String codigo, String expiraEm) {
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
        try (Connection connection = Conexao.abrir();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT id, nome, telefone FROM tutores ORDER BY nome")) {
            while (result.next()) {
                tutors.add(new Tutor(
                        result.getLong("id"),
                        result.getString("nome"),
                        result.getString("telefone")));
            }
        }
        return tutors;
    }

    public Tutor criarTutor(TutorRequest request) throws SQLException, GeneralSecurityException {
        if (request == null) {
            throw new IllegalArgumentException("Envie os dados do tutor.");
        }
        String nome = requireText(request.nome(), "Informe o nome do tutor.");
        String telefone = requireText(request.telefone(), "Informe o telefone do tutor.");
        String cpf = CpfCriptografia.normalizar(request.cpf());
        String cpfCifrado = CpfCriptografia.criptografar(cpf);

        try (Connection connection = Conexao.abrir()) {
            if (cpfJaExiste(connection, cpf)) {
                throw new CpfDuplicadoException();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO tutores (nome, cpf_cifrado, telefone) VALUES (?, ?, ?)")) {
                statement.setString(1, nome);
                statement.setString(2, cpfCifrado);
                statement.setString(3, telefone);
                statement.executeUpdate();
                return new Tutor(lastInsertedId(connection), nome, telefone);
            }
        }
    }

    public Tutor atualizarTutor(long id, TutorUpdateRequest request) throws SQLException {
        requireId(id);
        if (request == null) {
            throw new IllegalArgumentException("Envie os dados do tutor.");
        }
        String nome = requireText(request.nome(), "Informe o nome do tutor.");
        String telefone = requireText(request.telefone(), "Informe o telefone do tutor.");
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE tutores SET nome = ?, telefone = ? WHERE id = ?")) {
            statement.setString(1, nome);
            statement.setString(2, telefone);
            statement.setLong(3, id);
            if (statement.executeUpdate() == 0) {
                throw new NotFoundException();
            }
        }
        return new Tutor(id, nome, telefone);
    }

    public ClientInvitation gerarConviteCliente(long tutorId) throws SQLException {
        requireId(tutorId);
        String codigo = AuthService.gerarCodigoConvite();
        String codigoHash = AuthService.hashInvitationCode(codigo);
        long expiraEm = System.currentTimeMillis() + 24 * 60 * 60 * 1000L;

        try (Connection connection = Conexao.abrir()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement tutorQuery =
                             connection.prepareStatement("SELECT 1 FROM tutores WHERE id = ?");
                     PreparedStatement accountQuery =
                             connection.prepareStatement("SELECT 1 FROM usuarios WHERE tutor_id = ?")) {
                    tutorQuery.setLong(1, tutorId);
                    try (ResultSet result = tutorQuery.executeQuery()) {
                        if (!result.next()) {
                            throw new NotFoundException();
                        }
                    }
                    accountQuery.setLong(1, tutorId);
                    try (ResultSet result = accountQuery.executeQuery()) {
                        if (result.next()) {
                            throw new ClientAccountAlreadyLinkedException();
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE tutores
                        SET convite_hash = ?, convite_expira_em = ?
                        WHERE id = ?
                        """)) {
                    statement.setString(1, codigoHash);
                    statement.setLong(2, expiraEm);
                    statement.setLong(3, tutorId);
                    if (statement.executeUpdate() != 1) {
                        throw new NotFoundException();
                    }
                }
                connection.commit();
                return new ClientInvitation(codigo, Instant.ofEpochMilli(expiraEm).toString());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public List<Pet> listarPets(Long tutorId) throws SQLException {
        List<Pet> pets = new ArrayList<>();
        String sql = """
                SELECT p.id, p.tutor_id, t.nome AS tutor_nome, p.nome, p.especie, p.raca,
                       p.foto IS NOT NULL AS tem_foto
                FROM pets p
                JOIN tutores t ON t.id = p.tutor_id
                """ + (tutorId == null ? "" : "WHERE p.tutor_id = ? ") + "ORDER BY p.nome";
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (tutorId != null) {
                statement.setLong(1, tutorId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    pets.add(readPet(result));
                }
            }
        }
        return pets;
    }

    public Pet criarPet(PetRequest request, Long clientTutorId) throws SQLException {
        if (request == null) {
            throw new IllegalArgumentException("Envie os dados do pet.");
        }
        long tutorId = chooseTutorId(request.tutorId(), clientTutorId);
        String nome = requireText(request.nome(), "Informe o nome do pet.");
        String especie = requireText(request.especie(), "Informe a espécie do pet.");
        String raca = blankToNull(request.raca());
        String sql = "INSERT INTO pets (tutor_id, nome, especie, raca) VALUES (?, ?, ?, ?)";
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, tutorId);
            statement.setString(2, nome);
            statement.setString(3, especie);
            statement.setString(4, raca);
            statement.executeUpdate();
            return buscarPet(connection, lastInsertedId(connection));
        }
    }

    public Pet atualizarPet(long id, PetRequest request, Long clientTutorId) throws SQLException {
        requireId(id);
        if (request == null) {
            throw new IllegalArgumentException("Envie os dados do pet.");
        }
        long tutorId = chooseTutorId(request.tutorId(), clientTutorId);
        String nome = requireText(request.nome(), "Informe o nome do pet.");
        String especie = requireText(request.especie(), "Informe a espécie do pet.");
        String raca = blankToNull(request.raca());
        String sql = """
                UPDATE pets
                SET tutor_id = ?, nome = ?, especie = ?, raca = ?
                WHERE id = ?
                """ + (clientTutorId == null ? "" : "AND tutor_id = ?");
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, tutorId);
            statement.setString(2, nome);
            statement.setString(3, especie);
            statement.setString(4, raca);
            statement.setLong(5, id);
            if (clientTutorId != null) {
                statement.setLong(6, clientTutorId);
            }
            if (statement.executeUpdate() == 0) {
                throw new NotFoundException();
            }
            return buscarPet(connection, id);
        }
    }

    public List<Agendamento> listarAgendamentos(Long tutorId) throws SQLException {
        List<Agendamento> appointments = new ArrayList<>();
        String sql = """
                SELECT a.id, a.pet_id, p.nome AS pet, a.data, a.horario, a.motivo, a.status
                FROM agendamentos a
                JOIN pets p ON p.id = a.pet_id
                """ + (tutorId == null ? "" : "WHERE p.tutor_id = ? ")
                + "ORDER BY a.data, a.horario";
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (tutorId != null) {
                statement.setLong(1, tutorId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    appointments.add(readAppointment(result));
                }
            }
        }
        return appointments;
    }

    public Agendamento criarAgendamento(AgendamentoRequest request, Long clientTutorId) throws SQLException {
        if (request == null || request.petId() == null || request.petId() <= 0) {
            throw new IllegalArgumentException("Selecione um pet para o agendamento.");
        }
        String data = validarData(request.data());
        String horario = validarHorario(request.horario());
        String motivo = requireText(request.motivo(), "Informe o motivo do atendimento.");
        String sql = """
                INSERT INTO agendamentos (pet_id, data, horario, motivo)
                SELECT p.id, ?, ?, ?
                FROM pets p
                WHERE p.id = ? AND (? IS NULL OR p.tutor_id = ?)
                """;
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, data);
            statement.setString(2, horario);
            statement.setString(3, motivo);
            statement.setLong(4, request.petId());
            setNullableLong(statement, 5, clientTutorId);
            setNullableLong(statement, 6, clientTutorId);
            if (statement.executeUpdate() == 0) {
                throw new NotFoundException();
            }
            return buscarAgendamento(connection, lastInsertedId(connection));
        }
    }

    public Agendamento atualizarAgendamento(long id, AgendamentoUpdateRequest request) throws SQLException {
        requireId(id);
        if (request == null || request.petId() == null || request.petId() <= 0) {
            throw new IllegalArgumentException("Selecione um pet para o agendamento.");
        }
        String data = validarData(request.data());
        String horario = validarHorario(request.horario());
        String motivo = requireText(request.motivo(), "Informe o motivo do atendimento.");
        String status = validarStatus(request.status());
        String sql = """
                UPDATE agendamentos
                SET pet_id = ?, data = ?, horario = ?, motivo = ?, status = ?
                WHERE id = ?
                """;
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, request.petId());
            statement.setString(2, data);
            statement.setString(3, horario);
            statement.setString(4, motivo);
            statement.setString(5, status);
            statement.setLong(6, id);
            if (statement.executeUpdate() == 0) {
                throw new NotFoundException();
            }
            return buscarAgendamento(connection, id);
        }
    }

    public ClientProfile buscarPerfilCliente(AuthService.User user) throws SQLException {
        String sql = """
                SELECT t.nome, u.email, t.telefone, t.foto_perfil IS NOT NULL AS tem_foto
                FROM usuarios u
                JOIN tutores t ON t.id = u.tutor_id
                WHERE u.id = ? AND u.tutor_id = ?
                """;
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, user.id());
            statement.setLong(2, user.tutorId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new NotFoundException();
                }
                return new ClientProfile(
                        result.getString("nome"),
                        result.getString("email"),
                        result.getString("telefone"),
                        result.getBoolean("tem_foto"));
            }
        }
    }

    public Foto buscarFotoPerfilCliente(AuthService.User user) throws SQLException {
        String sql = """
                SELECT t.foto_perfil, t.foto_perfil_mime
                FROM usuarios u
                JOIN tutores t ON t.id = u.tutor_id
                WHERE u.id = ? AND u.tutor_id = ? AND t.foto_perfil IS NOT NULL
                """;
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, user.id());
            statement.setLong(2, user.tutorId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new NotFoundException();
                }
                return new Foto(result.getBytes("foto_perfil"), result.getString("foto_perfil_mime"));
            }
        }
    }

    public void salvarFotoPerfilCliente(AuthService.User user, ImagemUpload.Conteudo foto)
            throws SQLException {
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE tutores
                     SET foto_perfil = ?, foto_perfil_mime = ?
                     WHERE id = ? AND EXISTS (
                         SELECT 1 FROM usuarios WHERE id = ? AND tutor_id = tutores.id
                     )
                     """)) {
            statement.setBytes(1, foto.bytes());
            statement.setString(2, foto.tipoMime());
            statement.setLong(3, user.tutorId());
            statement.setLong(4, user.id());
            if (statement.executeUpdate() == 0) {
                throw new NotFoundException();
            }
        }
    }

    public void removerFotoPerfilCliente(AuthService.User user) throws SQLException {
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE tutores
                     SET foto_perfil = NULL, foto_perfil_mime = NULL
                     WHERE id = ? AND EXISTS (
                         SELECT 1 FROM usuarios WHERE id = ? AND tutor_id = tutores.id
                     )
                     """)) {
            statement.setLong(1, user.tutorId());
            statement.setLong(2, user.id());
            if (statement.executeUpdate() == 0) {
                throw new NotFoundException();
            }
        }
    }

    public Foto buscarFotoPet(long petId, Long tutorId) throws SQLException {
        requireId(petId);
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT foto, foto_mime
                     FROM pets
                     WHERE id = ? AND (? IS NULL OR tutor_id = ?) AND foto IS NOT NULL
                     """)) {
            statement.setLong(1, petId);
            setNullableLong(statement, 2, tutorId);
            setNullableLong(statement, 3, tutorId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new NotFoundException();
                }
                return new Foto(result.getBytes("foto"), result.getString("foto_mime"));
            }
        }
    }

    public void salvarFotoPet(long petId, Long tutorId, ImagemUpload.Conteudo foto) throws SQLException {
        requireId(petId);
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE pets SET foto = ?, foto_mime = ?
                     WHERE id = ? AND (? IS NULL OR tutor_id = ?)
                     """)) {
            statement.setBytes(1, foto.bytes());
            statement.setString(2, foto.tipoMime());
            statement.setLong(3, petId);
            setNullableLong(statement, 4, tutorId);
            setNullableLong(statement, 5, tutorId);
            if (statement.executeUpdate() == 0) {
                throw new NotFoundException();
            }
        }
    }

    public void removerFotoPet(long petId, Long tutorId) throws SQLException {
        requireId(petId);
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE pets SET foto = NULL, foto_mime = NULL
                     WHERE id = ? AND (? IS NULL OR tutor_id = ?)
                     """)) {
            statement.setLong(1, petId);
            setNullableLong(statement, 2, tutorId);
            setNullableLong(statement, 3, tutorId);
            if (statement.executeUpdate() == 0) {
                throw new NotFoundException();
            }
        }
    }

    public ClientProfile atualizarPerfilCliente(AuthService.User user, ClientProfileRequest request)
            throws SQLException {
        if (request == null) {
            throw new IllegalArgumentException("Envie os dados da conta.");
        }
        String nome = requireText(request.nome(), "Informe seu nome.");
        String email = normalizeEmail(request.email());
        String telefone = requireText(request.telefone(), "Informe seu telefone.");
        try (Connection connection = Conexao.abrir()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT 1 FROM usuarios WHERE lower(email) = ? AND id <> ?")) {
                    check.setString(1, email);
                    check.setLong(2, user.id());
                    try (ResultSet result = check.executeQuery()) {
                        if (result.next()) {
                            throw new AuthService.DuplicateEmailException();
                        }
                    }
                }
                try (PreparedStatement updateUser = connection.prepareStatement("""
                        UPDATE usuarios SET nome = ?, email = ?
                        WHERE id = ? AND tutor_id = ?
                        """);
                     PreparedStatement updateTutor = connection.prepareStatement(
                             "UPDATE tutores SET nome = ?, telefone = ? WHERE id = ?")) {
                    updateUser.setString(1, nome);
                    updateUser.setString(2, email);
                    updateUser.setLong(3, user.id());
                    updateUser.setLong(4, user.tutorId());
                    if (updateUser.executeUpdate() == 0) {
                        throw new NotFoundException();
                    }
                    updateTutor.setString(1, nome);
                    updateTutor.setString(2, telefone);
                    updateTutor.setLong(3, user.tutorId());
                    if (updateTutor.executeUpdate() == 0) {
                        throw new NotFoundException();
                    }
                }
                connection.commit();
                return buscarPerfilCliente(user);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public boolean removerTutor(long id) throws SQLException {
        return remover("tutores", id);
    }

    public boolean removerPet(long id, Long tutorId) throws SQLException {
        requireId(id);
        try (Connection connection = Conexao.abrir()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT 1 FROM pets WHERE id = ? AND (? IS NULL OR tutor_id = ?)")) {
                    query.setLong(1, id);
                    setNullableLong(query, 2, tutorId);
                    setNullableLong(query, 3, tutorId);
                    try (ResultSet result = query.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT 1 FROM agendamentos WHERE pet_id = ? LIMIT 1")) {
                    query.setLong(1, id);
                    try (ResultSet result = query.executeQuery()) {
                        if (result.next()) {
                            throw new PetPossuiAgendamentosException();
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM pets WHERE id = ? AND (? IS NULL OR tutor_id = ?)")) {
                    statement.setLong(1, id);
                    setNullableLong(statement, 2, tutorId);
                    setNullableLong(statement, 3, tutorId);
                    boolean removido = statement.executeUpdate() > 0;
                    connection.commit();
                    return removido;
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public boolean cancelarAgendamento(long id, Long clientTutorId) throws SQLException {
        requireId(id);
        String sql = """
                UPDATE agendamentos
                SET status = 'CANCELADO'
                WHERE id = ?
                """ + (clientTutorId == null ? "" : """
                        AND EXISTS (
                            SELECT 1 FROM pets p
                            WHERE p.id = agendamentos.pet_id AND p.tutor_id = ?
                        )
                        """);
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            if (clientTutorId != null) {
                statement.setLong(2, clientTutorId);
            }
            return statement.executeUpdate() > 0;
        }
    }

    private boolean cpfJaExiste(Connection connection, String cpf)
            throws SQLException, GeneralSecurityException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT cpf_cifrado FROM tutores WHERE cpf_cifrado IS NOT NULL")) {
            while (result.next()) {
                if (CpfCriptografia.corresponde(result.getString(1), cpf)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Pet buscarPet(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.id, p.tutor_id, t.nome AS tutor_nome, p.nome, p.especie, p.raca,
                       p.foto IS NOT NULL AS tem_foto
                FROM pets p
                JOIN tutores t ON t.id = p.tutor_id
                WHERE p.id = ?
                """)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new NotFoundException();
                }
                return readPet(result);
            }
        }
    }

    private Agendamento buscarAgendamento(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT a.id, a.pet_id, p.nome AS pet, a.data, a.horario, a.motivo, a.status
                FROM agendamentos a
                JOIN pets p ON p.id = a.pet_id
                WHERE a.id = ?
                """)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new NotFoundException();
                }
                return readAppointment(result);
            }
        }
    }

    private boolean remover(String tableName, long id) throws SQLException {
        requireId(id);
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

    private Pet readPet(ResultSet result) throws SQLException {
        return new Pet(
                result.getLong("id"),
                result.getLong("tutor_id"),
                result.getString("tutor_nome"),
                result.getString("nome"),
                result.getString("especie"),
                result.getString("raca"),
                result.getBoolean("tem_foto"));
    }

    private Agendamento readAppointment(ResultSet result) throws SQLException {
        return new Agendamento(
                result.getLong("id"),
                result.getLong("pet_id"),
                result.getString("pet"),
                result.getString("data"),
                result.getString("horario"),
                result.getString("motivo"),
                result.getString("status"));
    }

    private long chooseTutorId(Long requestedTutorId, Long clientTutorId) {
        long tutorId = clientTutorId != null
                ? clientTutorId
                : requestedTutorId == null ? 0 : requestedTutorId;
        if (tutorId <= 0) {
            throw new IllegalArgumentException("Selecione um tutor para o pet.");
        }
        return tutorId;
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

    private String validarStatus(String value) {
        String status = requireText(value, "Informe o status do agendamento.").toUpperCase(Locale.ROOT);
        if (!List.of("AGENDADO", "CONFIRMADO", "CONCLUIDO", "CANCELADO").contains(status)) {
            throw new IllegalArgumentException("O status do agendamento é inválido.");
        }
        return status;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        return normalized;
    }

    private void requireId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("O identificador do registro é inválido.");
        }
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
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

    public static final class NotFoundException extends RuntimeException {
        public NotFoundException() {
            super("Registro não encontrado.");
        }
    }

    public static final class ClientAccountAlreadyLinkedException extends RuntimeException {
        public ClientAccountAlreadyLinkedException() {
            super("Este tutor já possui uma conta de cliente vinculada.");
        }
    }

    public static final class PetPossuiAgendamentosException extends RuntimeException {
        public PetPossuiAgendamentosException() {
            super("Este pet possui agendamentos vinculados e não pode ser removido para preservar o histórico.");
        }
    }
}
