package br.com.clinicaveterinaria;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class Main {
    private static final int DEFAULT_PORT = 8081;

    private Main() {
    }

    public static void main(String[] args) throws IOException, java.sql.SQLException {
        if (args.length == 1 && args[0].equals("--redefinir-senha")) {
            CredentialSetup.executar();
            return;
        }
        if (args.length != 0) {
            throw new IllegalArgumentException("Argumento de inicialização não reconhecido.");
        }

        DatabaseMigration.aplicar();
        int port = readPort();
        ClinicaService service = new ClinicaService();
        AuthService authService = new AuthService();
        long tutorCount = service.contarTutores();
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
        server.createContext("/", new ApiServer(service, authService)::handle);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        System.out.println("Conexão SQLite estabelecida.");
        System.out.println("Tutores cadastrados: " + tutorCount);
        System.out.println("Site e API disponíveis em http://127.0.0.1:" + port);
        if (System.getenv("CLINICA_CPF_KEY") == null) {
            System.out.println("Para cadastrar tutores com CPF, configure CLINICA_CPF_KEY.");
        }
    }

    private static int readPort() {
        String configuredPort = System.getenv("CLINICA_PORT");
        if (configuredPort == null || configuredPort.isBlank()) {
            return DEFAULT_PORT;
        }

        try {
            int port = Integer.parseInt(configuredPort);
            if (port < 1 || port > 65535) {
                throw new IllegalStateException("CLINICA_PORT deve estar entre 1 e 65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("CLINICA_PORT deve ser um número entre 1 e 65535.", exception);
        }
    }
}
