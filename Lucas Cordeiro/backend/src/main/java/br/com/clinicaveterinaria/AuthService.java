package br.com.clinicaveterinaria;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.mindrot.jbcrypt.BCrypt;

public final class AuthService {
    private static final int BCRYPT_COST = 12;
    private static final long SESSION_LIFETIME_MILLIS = 8 * 60 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record User(long id, String nome, String email, String perfil, Long tutorId) {
        public boolean isClient() {
            return tutorId != null;
        }

        public boolean isStaff() {
            return tutorId == null && ("ADMIN".equals(perfil) || "FUNCIONARIO".equals(perfil));
        }

        public String effectiveProfile() {
            return isClient() ? "CLIENTE" : perfil;
        }
    }

    public record PublicUser(long id, String nome, String email, String perfil) {
    }

    public record SessionData(User user, String csrfToken) {
    }

    public record SessionResult(String token, String csrfToken, User user) {
    }

    public record ClientRegistration(String nome, String email, String senha, String telefone, String cpf) {
    }

    public record ClientInviteRegistration(String codigo, String email, String senha) {
    }

    private record StoredSession(User user, String csrfToken, long expiresAt) {
    }

    private final Map<String, StoredSession> sessions = new ConcurrentHashMap<>();

    public SessionResult login(String email, String senha) throws SQLException {
        String normalizedEmail = normalizeEmail(email);
        if (senha == null || senha.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        User user = null;
        String storedHash = null;
        String sql = """
                SELECT id, nome, email, senha_hash, perfil, tutor_id
                FROM usuarios
                WHERE lower(email) = ?
                """;
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedEmail);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    user = readUser(result);
                    storedHash = result.getString("senha_hash");
                }
            }
        }

        if (user == null || !isValidBCryptHash(storedHash) || !checkPassword(senha, storedHash)) {
            throw new InvalidCredentialsException();
        }
        return createSession(user);
    }

    public SessionResult registrarCliente(ClientRegistration request)
            throws SQLException, GeneralSecurityException {
        if (request == null) {
            throw new IllegalArgumentException("Envie os dados para criar a conta.");
        }
        String nome = requireText(request.nome(), "Informe seu nome.");
        String email = normalizeEmail(request.email());
        String telefone = requireText(request.telefone(), "Informe seu telefone.");
        String senha = requirePassword(request.senha());
        String cpf = CpfCriptografia.normalizar(request.cpf());
        String cpfCifrado = CpfCriptografia.criptografar(cpf);
        String senhaHash = BCrypt.hashpw(senha, BCrypt.gensalt(BCRYPT_COST));

        try (Connection connection = Conexao.abrir()) {
            connection.setAutoCommit(false);
            try {
                if (emailExists(connection, email)) {
                    throw new DuplicateEmailException();
                }
                if (cpfExists(connection, cpf)) {
                    throw new ClinicaService.CpfDuplicadoException();
                }

                long tutorId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO tutores (nome, cpf_cifrado, telefone) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, nome);
                    statement.setString(2, cpfCifrado);
                    statement.setString(3, telefone);
                    statement.executeUpdate();
                    tutorId = lastInsertedId(connection, statement);
                }

                String insertUser = """
                        INSERT INTO usuarios (nome, email, senha_hash, perfil, tutor_id)
                        VALUES (?, ?, ?, 'FUNCIONARIO', ?)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(insertUser)) {
                    statement.setString(1, nome);
                    statement.setString(2, email);
                    statement.setString(3, senhaHash);
                    statement.setLong(4, tutorId);
                    statement.executeUpdate();
                }
                User user = findUserByEmail(connection, email);
                connection.commit();
                return createSession(user);
            } catch (SQLException | RuntimeException | GeneralSecurityException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public SessionResult registrarClienteComConvite(ClientInviteRegistration request) throws SQLException {
        if (request == null) {
            throw new InvalidInvitationException();
        }
        String codigo = request.codigo() == null ? "" : request.codigo().trim();
        if (!codigo.matches("^[A-Za-z0-9_-]{40,64}$")) {
            throw new InvalidInvitationException();
        }
        String email = normalizeEmail(request.email());
        String senha = requirePassword(request.senha());
        String senhaHash = BCrypt.hashpw(senha, BCrypt.gensalt(BCRYPT_COST));
        String codigoHash = hashInvitationCode(codigo);

        try (Connection connection = Conexao.abrir()) {
            connection.setAutoCommit(false);
            try {
                if (emailExists(connection, email)) {
                    throw new DuplicateEmailException();
                }

                long tutorId;
                String nome;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT id, nome
                        FROM tutores
                        WHERE convite_hash = ? AND convite_expira_em > ?
                        """)) {
                    statement.setString(1, codigoHash);
                    statement.setLong(2, System.currentTimeMillis());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new InvalidInvitationException();
                        }
                        tutorId = result.getLong("id");
                        nome = result.getString("nome");
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE tutores
                        SET convite_hash = NULL, convite_expira_em = NULL
                        WHERE id = ? AND convite_hash = ? AND convite_expira_em > ?
                        """)) {
                    statement.setLong(1, tutorId);
                    statement.setString(2, codigoHash);
                    statement.setLong(3, System.currentTimeMillis());
                    if (statement.executeUpdate() != 1) {
                        throw new InvalidInvitationException();
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO usuarios (nome, email, senha_hash, perfil, tutor_id)
                        VALUES (?, ?, ?, 'FUNCIONARIO', ?)
                        """)) {
                    statement.setString(1, nome);
                    statement.setString(2, email);
                    statement.setString(3, senhaHash);
                    statement.setLong(4, tutorId);
                    statement.executeUpdate();
                }
                User user = findUserByEmail(connection, email);
                connection.commit();
                return createSession(user);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public SessionData findSession(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        StoredSession session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAt() <= System.currentTimeMillis()) {
            sessions.remove(token, session);
            return null;
        }
        return new SessionData(session.user(), session.csrfToken());
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public boolean csrfMatches(SessionData session, String token) {
        return session != null && token != null
                && MessageDigest.isEqual(
                        session.csrfToken().getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8));
    }

    public static void redefinirSenhaFuncionario(String email, char[] senha)
            throws SQLException {
        String normalizedEmail = normalizeEmail(email);
        String plainPassword = requirePassword(senha == null ? null : new String(senha));
        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_COST));
        String sql = """
                UPDATE usuarios
                SET senha_hash = ?
                WHERE lower(email) = ?
                  AND tutor_id IS NULL
                  AND perfil IN ('ADMIN', 'FUNCIONARIO')
                """;
        try (Connection connection = Conexao.abrir();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash);
            statement.setString(2, normalizedEmail);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException(
                        "Nenhuma conta de funcionário com esse e-mail foi encontrada.");
            }
        }
    }

    private static String gerarTokenAleatorio() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String gerarCodigoConvite() {
        return gerarTokenAleatorio();
    }

    public static String hashInvitationCode(String codigo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codigo.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível nesta instalação do Java.", exception);
        }
    }

    public static String requirePassword(String password) {
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 12 caracteres.");
        }
        if (password.length() > 72) {
            throw new IllegalArgumentException("A senha deve ter no máximo 72 caracteres.");
        }
        return password;
    }

    private SessionResult createSession(User user) {
        String token = gerarTokenAleatorio();
        String csrfToken = gerarTokenAleatorio();
        sessions.put(token, new StoredSession(
                user, csrfToken, System.currentTimeMillis() + SESSION_LIFETIME_MILLIS));
        return new SessionResult(token, csrfToken, user);
    }

    private boolean emailExists(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT 1 FROM usuarios WHERE lower(email) = ?")) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean cpfExists(Connection connection, String cpf)
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

    private User findUserByEmail(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, nome, email, perfil, tutor_id
                FROM usuarios
                WHERE lower(email) = ?
                """)) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("A conta recém-criada não foi localizada.");
                }
                return readUser(result);
            }
        }
    }

    private static User readUser(ResultSet result) throws SQLException {
        long tutorId = result.getLong("tutor_id");
        Long linkedTutorId = result.wasNull() ? null : tutorId;
        return new User(
                result.getLong("id"),
                result.getString("nome"),
                result.getString("email"),
                result.getString("perfil"),
                linkedTutorId);
    }

    private long lastInsertedId(Connection connection, PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
        }
        try (Statement query = connection.createStatement();
             ResultSet result = query.executeQuery("SELECT last_insert_rowid()")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static boolean checkPassword(String password, String hash) {
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isValidBCryptHash(String hash) {
        return hash != null && hash.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() > 254 || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        return normalized;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public static final class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("E-mail ou senha incorretos.");
        }
    }

    public static final class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException() {
            super("Este e-mail já possui uma conta.");
        }
    }

    public static final class InvalidInvitationException extends RuntimeException {
        public InvalidInvitationException() {
            super("Convite inválido, expirado ou já utilizado. Solicite outro à clínica.");
        }
    }
}
