package br.com.clinicaveterinaria;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Map;

public final class ApiServer implements HttpHandler {
    private static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final Map<String, String> STATIC_FILES = Map.of(
            "/", "index.html",
            "/index.html", "index.html",
            "/styles.css", "styles.css",
            "/script.js", "script.js");

    private final ClinicaService service;
    private final Gson gson = new Gson();
    private final Path projectRoot;

    public ApiServer(ClinicaService service) throws SQLException, IOException {
        this.service = service;
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
        } catch (ClinicaService.CpfDuplicadoException exception) {
            sendJson(exchange, 409, Map.of("erro", exception.getMessage()));
        } catch (JsonParseException | IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("erro", exception.getMessage()));
        } catch (IllegalStateException exception) {
            sendJson(exchange, 503, Map.of("erro", exception.getMessage()));
        } catch (SQLException exception) {
            exception.printStackTrace(System.err);
            int status = exception.getErrorCode() == 19 ? 409 : 500;
            String message = status == 409
                    ? "O registro não pode ser removido porque ainda possui registros relacionados."
                    : "Erro ao acessar o banco de dados.";
            sendJson(exchange, status, Map.of("erro", message));
        } catch (GeneralSecurityException exception) {
            exception.printStackTrace(System.err);
            sendJson(exchange, 500, Map.of("erro", "Não foi possível proteger o CPF."));
        }
    }

    private void handleApi(HttpExchange exchange, String path)
            throws IOException, SQLException, GeneralSecurityException {
        if (path.equals("/api/health")) {
            if (!requireMethod(exchange, "GET")) {
                return;
            }
            sendJson(exchange, 200, Map.of("status", "ok"));
            return;
        }

        String[] parts = path.split("/");
        if (parts.length < 3 || parts.length > 4) {
            throw new ApiException(404, "Rota da API não encontrada.");
        }

        String resource = parts[2];
        if (parts.length == 3) {
            handleCollection(exchange, resource);
            return;
        }

        long id = parseId(parts[3]);
        handleDelete(exchange, resource, id);
    }

    private void handleCollection(HttpExchange exchange, String resource)
            throws IOException, SQLException, GeneralSecurityException {
        String method = exchange.getRequestMethod();
        switch (resource) {
            case "tutores" -> {
                if (method.equals("GET")) {
                    sendJson(exchange, 200, service.listarTutores());
                } else if (method.equals("POST")) {
                    ClinicaService.TutorRequest request =
                            readJson(exchange, ClinicaService.TutorRequest.class);
                    if (request == null) {
                        throw new ApiException(400, "Envie os dados do tutor.");
                    }
                    sendJson(exchange, 201, service.criarTutor(request));
                } else {
                    methodNotAllowed(exchange);
                }
            }
            case "pets" -> {
                if (method.equals("GET")) {
                    sendJson(exchange, 200, service.listarPets());
                } else if (method.equals("POST")) {
                    ClinicaService.PetRequest request =
                            readJson(exchange, ClinicaService.PetRequest.class);
                    if (request == null) {
                        throw new ApiException(400, "Envie os dados do pet.");
                    }
                    sendJson(exchange, 201, service.criarPet(request));
                } else {
                    methodNotAllowed(exchange);
                }
            }
            case "agendamentos" -> {
                if (method.equals("GET")) {
                    sendJson(exchange, 200, service.listarAgendamentos());
                } else if (method.equals("POST")) {
                    ClinicaService.AgendamentoRequest request =
                            readJson(exchange, ClinicaService.AgendamentoRequest.class);
                    if (request == null) {
                        throw new ApiException(400, "Envie os dados do agendamento.");
                    }
                    sendJson(exchange, 201, service.criarAgendamento(request));
                } else {
                    methodNotAllowed(exchange);
                }
            }
            default -> throw new ApiException(404, "Recurso da API não encontrado.");
        }
    }

    private void handleDelete(HttpExchange exchange, String resource, long id)
            throws IOException, SQLException {
        if (!requireMethod(exchange, "DELETE")) {
            return;
        }
        boolean removed = switch (resource) {
            case "tutores" -> service.removerTutor(id);
            case "pets" -> service.removerPet(id);
            case "agendamentos" -> service.removerAgendamento(id);
            default -> throw new ApiException(404, "Recurso da API não encontrado.");
        };
        if (!removed) {
            throw new ApiException(404, "Registro não encontrado.");
        }
        sendJson(exchange, 200, Map.of("mensagem", "Registro removido."));
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

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            methodNotAllowed(exchange);
            return;
        }

        String fileName = STATIC_FILES.get(path);
        if (fileName == null) {
            sendJson(exchange, 404, Map.of("erro", "Página não encontrada."));
            return;
        }

        byte[] content = Files.readAllBytes(projectRoot.resolve(fileName));
        String contentType = fileName.endsWith(".css")
                ? "text/css; charset=utf-8"
                : fileName.endsWith(".js") ? "text/javascript; charset=utf-8" : "text/html; charset=utf-8";
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(content);
        }
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

    private boolean requireMethod(HttpExchange exchange, String expected) throws IOException {
        if (!exchange.getRequestMethod().equals(expected)) {
            methodNotAllowed(exchange);
            return false;
        }
        return true;
    }

    private void methodNotAllowed(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Allow", "GET, POST, DELETE");
        sendJson(exchange, 405, Map.of("erro", "Método HTTP não permitido."));
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] content = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(content);
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
