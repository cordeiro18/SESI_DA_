package br.com.clinicaveterinaria;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiServer implements HttpHandler {
    private static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final String SESSION_COOKIE = "DOGMANIA_SESSION";
    private static final long SESSION_MAX_AGE_SECONDS = 8 * 60 * 60;
    private static final Pattern RESOURCE_PATH =
            Pattern.compile("^/api/(tutores|pets|agendamentos)(?:/(\\d+))?$");
    private static final Pattern CLIENT_INVITE_PATH =
            Pattern.compile("^/api/tutores/(\\d+)/convite$");
    private static final Map<String, String> STATIC_FILES = Map.of(
            "/login", "login.html",
            "/login.html", "login.html",
            "/login.js", "login.js",
            "/styles.css", "styles.css",
            "/script.js", "script.js",
            "/cliente.js", "cliente.js");

    private final ClinicaService service;
    private final AuthService authService;
    private final Gson gson = new Gson();
    private final Path projectRoot;

    public ApiServer(ClinicaService service, AuthService authService) throws IOException {
        this.service = service;
        this.authService = authService;
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("index.html"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IOException("Não foi possível localizar index.html na pasta do projeto.");
        }
        projectRoot = current;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
            } else {
                serveStatic(exchange, path);
            }
        } catch (ApiException exception) {
            sendJson(exchange, exception.statusCode, Map.of("erro", exception.getMessage()));
        } catch (AuthService.InvalidCredentialsException exception) {
            sendJson(exchange, 401, Map.of("erro", exception.getMessage()));
        } catch (AuthService.DuplicateEmailException | ClinicaService.CpfDuplicadoException exception) {
            sendJson(exchange, 409, Map.of("erro", exception.getMessage()));
        } catch (ClinicaService.ClientAccountAlreadyLinkedException exception) {
            sendJson(exchange, 409, Map.of("erro", exception.getMessage()));
        } catch (AuthService.InvalidInvitationException exception) {
            sendJson(exchange, 400, Map.of("erro", exception.getMessage()));
        } catch (ClinicaService.NotFoundException exception) {
            sendJson(exchange, 404, Map.of("erro", exception.getMessage()));
        } catch (JsonParseException | IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("erro", exception.getMessage()));
        } catch (IllegalStateException exception) {
            sendJson(exchange, 503, Map.of("erro", exception.getMessage()));
        } catch (SQLException exception) {
            exception.printStackTrace(System.err);
            int status = exception.getErrorCode() == 19 ? 409 : 500;
            String message = status == 409
                    ? "O registro conflita com dados já existentes ou possui registros relacionados."
                    : "Erro ao acessar o banco de dados.";
            sendJson(exchange, status, Map.of("erro", message));
        } catch (GeneralSecurityException exception) {
            exception.printStackTrace(System.err);
            sendJson(exchange, 500, Map.of("erro", "Não foi possível proteger os dados do cadastro."));
        } catch (RuntimeException exception) {
            exception.printStackTrace(System.err);
            sendJson(exchange, 500, Map.of("erro", "Ocorreu um erro interno ao processar a solicitação."));
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange, String path)
            throws IOException, SQLException, GeneralSecurityException {
        if (path.equals("/api/health")) {
            if (requireMethod(exchange, "GET")) {
                sendJson(exchange, 200, Map.of("status", "ok"));
            }
            return;
        }
        if (handlePublicAuth(exchange, path)) {
            return;
        }

        AuthService.SessionData session = requireSession(exchange);
        String method = exchange.getRequestMethod();
        if (!method.equals("GET")) {
            requireCsrf(exchange, session);
        }

        if (path.equals("/api/auth/me")) {
            if (requireMethod(exchange, "GET")) {
                sendJson(exchange, 200, Map.of(
                        "usuario", toPublicUser(session.user()),
                        "csrfToken", session.csrfToken()));
            }
            return;
        }
        if (path.equals("/api/auth/logout")) {
            if (!requireMethod(exchange, "POST")) {
                return;
            }
            authService.logout(readSessionToken(exchange));
            expireSessionCookie(exchange);
            sendJson(exchange, 200, Map.of("mensagem", "Sessão encerrada."));
            return;
        }
        if (path.equals("/api/cliente/me")) {
            handleClientProfile(exchange, session.user());
            return;
        }

        Matcher inviteMatcher = CLIENT_INVITE_PATH.matcher(path);
        if (inviteMatcher.matches()) {
            requireStaff(session.user());
            if (!requireMethod(exchange, "POST")) {
                return;
            }
            sendJson(exchange, 201, service.gerarConviteCliente(parseId(inviteMatcher.group(1))));
            return;
        }

        Matcher matcher = RESOURCE_PATH.matcher(path);
        if (!matcher.matches()) {
            throw new ApiException(404, "Rota da API não encontrada.");
        }
        String resource = matcher.group(1);
        Long id = matcher.group(2) == null ? null : parseId(matcher.group(2));
        handleResource(exchange, resource, id, session.user());
    }

    private boolean handlePublicAuth(HttpExchange exchange, String path)
            throws IOException, SQLException, GeneralSecurityException {
        if (path.equals("/api/auth/login")) {
            if (!requireMethod(exchange, "POST")) {
                return true;
            }
            LoginRequest request = readJson(exchange, LoginRequest.class);
            if (request == null) {
                throw new ApiException(400, "Informe e-mail e senha.");
            }
            setSessionCookie(exchange, authService.login(request.email(), request.senha()));
            return true;
        }
        if (path.equals("/api/auth/register")) {
            if (!requireMethod(exchange, "POST")) {
                return true;
            }
            AuthService.ClientRegistration request =
                    readJson(exchange, AuthService.ClientRegistration.class);
            if (request == null) {
                throw new ApiException(400, "Envie os dados para criar a conta.");
            }
            setSessionCookie(exchange, authService.registrarCliente(request));
            return true;
        }
        if (path.equals("/api/auth/register-existing")) {
            if (!requireMethod(exchange, "POST")) {
                return true;
            }
            AuthService.ClientInviteRegistration request =
                    readJson(exchange, AuthService.ClientInviteRegistration.class);
            if (request == null) {
                throw new ApiException(400, "Informe o código do convite, e-mail e senha.");
            }
            setSessionCookie(exchange, authService.registrarClienteComConvite(request));
            return true;
        }
        return false;
    }

    private void setSessionCookie(HttpExchange exchange, AuthService.SessionResult result)
            throws IOException {
        exchange.getResponseHeaders().add("Set-Cookie",
                SESSION_COOKIE + "=" + result.token()
                        + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + SESSION_MAX_AGE_SECONDS);
        sendJson(exchange, 200, Map.of(
                "usuario", toPublicUser(result.user()),
                "csrfToken", result.csrfToken()));
    }

    private void handleClientProfile(HttpExchange exchange, AuthService.User user)
            throws IOException, SQLException {
        if (!user.isClient()) {
            throw new ApiException(403, "Este recurso está disponível apenas para clientes.");
        }
        switch (exchange.getRequestMethod()) {
            case "GET" -> sendJson(exchange, 200, service.buscarPerfilCliente(user));
            case "PUT" -> sendJson(exchange, 200, service.atualizarPerfilCliente(
                    user, readJson(exchange, ClinicaService.ClientProfileRequest.class)));
            default -> methodNotAllowed(exchange, "GET, PUT");
        }
    }

    private void handleResource(HttpExchange exchange, String resource, Long id, AuthService.User user)
            throws IOException, SQLException, GeneralSecurityException {
        String method = exchange.getRequestMethod();
        Long clientTutorId = user.isClient() ? user.tutorId() : null;
        if (resource.equals("tutores")) {
            requireStaff(user);
            handleTutors(exchange, id, method);
            return;
        }
        if (resource.equals("pets")) {
            handlePets(exchange, id, method, user, clientTutorId);
            return;
        }
        handleAppointments(exchange, id, method, user, clientTutorId);
    }

    private void handleTutors(HttpExchange exchange, Long id, String method)
            throws IOException, SQLException, GeneralSecurityException {
        if (id == null) {
            switch (method) {
                case "GET" -> sendJson(exchange, 200, service.listarTutores());
                case "POST" -> sendJson(exchange, 201, service.criarTutor(
                        readJson(exchange, ClinicaService.TutorRequest.class)));
                default -> methodNotAllowed(exchange, "GET, POST");
            }
            return;
        }
        switch (method) {
            case "PUT" -> sendJson(exchange, 200, service.atualizarTutor(
                    id, readJson(exchange, ClinicaService.TutorUpdateRequest.class)));
            case "DELETE" -> removeTutor(exchange, id);
            default -> methodNotAllowed(exchange, "PUT, DELETE");
        }
    }

    private void handlePets(HttpExchange exchange, Long id, String method, AuthService.User user, Long clientTutorId)
            throws IOException, SQLException {
        if (id == null) {
            switch (method) {
                case "GET" -> sendJson(exchange, 200, service.listarPets(clientTutorId));
                case "POST" -> sendJson(exchange, 201, service.criarPet(
                        readJson(exchange, ClinicaService.PetRequest.class), clientTutorId));
                default -> methodNotAllowed(exchange, "GET, POST");
            }
            return;
        }
        switch (method) {
            case "PUT" -> sendJson(exchange, 200, service.atualizarPet(
                    id, readJson(exchange, ClinicaService.PetRequest.class), clientTutorId));
            case "DELETE" -> {
                requireStaff(user);
                removePet(exchange, id);
            }
            default -> methodNotAllowed(exchange, "PUT, DELETE");
        }
    }

    private void handleAppointments(
            HttpExchange exchange, Long id, String method, AuthService.User user, Long clientTutorId)
            throws IOException, SQLException {
        if (id == null) {
            switch (method) {
                case "GET" -> sendJson(exchange, 200, service.listarAgendamentos(clientTutorId));
                case "POST" -> sendJson(exchange, 201, service.criarAgendamento(
                        readJson(exchange, ClinicaService.AgendamentoRequest.class), clientTutorId));
                default -> methodNotAllowed(exchange, "GET, POST");
            }
            return;
        }
        switch (method) {
            case "PUT" -> {
                requireStaff(user);
                sendJson(exchange, 200, service.atualizarAgendamento(
                        id, readJson(exchange, ClinicaService.AgendamentoUpdateRequest.class)));
            }
            case "DELETE" -> {
                if (!service.cancelarAgendamento(id, clientTutorId)) {
                    throw new ApiException(404, "Agendamento não encontrado.");
                }
                sendJson(exchange, 200, Map.of("mensagem", "Agendamento cancelado."));
            }
            default -> methodNotAllowed(exchange, "GET, POST, PUT, DELETE");
        }
    }

    private void removeTutor(HttpExchange exchange, long id) throws IOException, SQLException {
        if (!service.removerTutor(id)) {
            throw new ClinicaService.NotFoundException();
        }
        sendJson(exchange, 200, Map.of("mensagem", "Tutor removido."));
    }

    private void removePet(HttpExchange exchange, long id) throws IOException, SQLException {
        if (!service.removerPet(id)) {
            throw new ClinicaService.NotFoundException();
        }
        sendJson(exchange, 200, Map.of("mensagem", "Pet removido."));
    }

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            methodNotAllowed(exchange, "GET");
            return;
        }

        AuthService.SessionData session = authService.findSession(readSessionToken(exchange));
        if (path.equals("/login") || path.equals("/login.html")) {
            if (session != null) {
                redirect(exchange, session.user().isClient() ? "/cliente" : "/funcionario");
            } else {
                sendStaticFile(exchange, "login.html");
            }
            return;
        }
        if (path.equals("/") || path.equals("/index.html")) {
            if (session != null) {
                redirect(exchange, session.user().isClient() ? "/cliente" : "/funcionario");
            } else if (path.equals("/index.html")) {
                redirect(exchange, "/login");
            } else {
                sendStaticFile(exchange, "login.html");
            }
            return;
        }
        if (path.equals("/funcionario")) {
            if (session == null) {
                redirect(exchange, "/login");
            } else if (!session.user().isStaff()) {
                redirect(exchange, "/cliente");
            } else {
                sendStaticFile(exchange, "index.html");
            }
            return;
        }
        if (path.equals("/cliente")) {
            if (session == null) {
                redirect(exchange, "/login");
            } else if (!session.user().isClient()) {
                redirect(exchange, "/funcionario");
            } else {
                sendStaticFile(exchange, "cliente.html");
            }
            return;
        }
        String fileName = STATIC_FILES.get(path);
        if (fileName == null) {
            sendJson(exchange, 404, Map.of("erro", "Página não encontrada."));
            return;
        }
        sendStaticFile(exchange, fileName);
    }

    private void sendStaticFile(HttpExchange exchange, String fileName) throws IOException {
        byte[] content = Files.readAllBytes(projectRoot.resolve(fileName));
        String contentType = fileName.endsWith(".css")
                ? "text/css; charset=utf-8"
                : fileName.endsWith(".js") ? "text/javascript; charset=utf-8" : "text/html; charset=utf-8";
        setSecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(content);
        }
    }

    private AuthService.SessionData requireSession(HttpExchange exchange) {
        AuthService.SessionData session = authService.findSession(readSessionToken(exchange));
        if (session == null) {
            throw new ApiException(401, "Faça login para continuar.");
        }
        return session;
    }

    private void requireStaff(AuthService.User user) {
        if (!user.isStaff()) {
            throw new ApiException(403, "Acesso permitido somente para funcionários.");
        }
    }

    private void requireCsrf(HttpExchange exchange, AuthService.SessionData session) {
        String csrfToken = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
        if (!authService.csrfMatches(session, csrfToken)) {
            throw new ApiException(403, "Token de segurança inválido. Atualize a página e tente novamente.");
        }
    }

    private String readSessionToken(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) {
            return null;
        }
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && SESSION_COOKIE.equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    private void expireSessionCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Set-Cookie",
                SESSION_COOKIE + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
    }

    private <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new ApiException(415, "Envie os dados no formato application/json.");
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new ApiException(413, "A requisição excede o tamanho permitido.");
        }
        return gson.fromJson(new String(body, StandardCharsets.UTF_8), type);
    }

    private long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("O identificador do registro é inválido.", exception);
        }
    }

    private AuthService.PublicUser toPublicUser(AuthService.User user) {
        return new AuthService.PublicUser(
                user.id(), user.nome(), user.email(), user.effectiveProfile());
    }

    private boolean requireMethod(HttpExchange exchange, String expected) throws IOException {
        if (!exchange.getRequestMethod().equals(expected)) {
            methodNotAllowed(exchange, expected);
            return false;
        }
        return true;
    }

    private void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        sendJson(exchange, 405, Map.of("erro", "Método HTTP não permitido."));
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        setSecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] content = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        setSecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(content);
        }
    }

    private void setSecurityHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Referrer-Policy", "same-origin");
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; "
                        + "img-src 'self' data:; connect-src 'self'; base-uri 'self'; "
                        + "form-action 'self'; frame-ancestors 'none'");
    }

    private static final class LoginRequest {
        private String email;
        private String senha;

        public String email() {
            return email;
        }

        public String senha() {
            return senha;
        }
    }

    private static final class ApiException extends RuntimeException {
        private final int statusCode;

        private ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
