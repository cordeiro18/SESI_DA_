package br.com.clinicaveterinaria;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class ImagemUpload {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000L;

    public record Conteudo(byte[] bytes, String tipoMime) {
    }

    private ImagemUpload() {
    }

    public static Conteudo validar(String tipoMime, String base64) {
        if (tipoMime == null || base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("Selecione uma imagem PNG ou JPEG.");
        }
        String mime = tipoMime.trim().toLowerCase(Locale.ROOT);
        String formatoEsperado = switch (mime) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpeg";
            default -> throw new IllegalArgumentException("Envie uma imagem PNG ou JPEG.");
        };
        int maxBase64Length = ((MAX_BYTES + 2) / 3) * 4;
        if (base64.length() > maxBase64Length) {
            throw new IllegalArgumentException("A imagem deve ter no máximo 2 MB.");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("O arquivo de imagem é inválido.", exception);
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("A imagem deve ter no máximo 2 MB.");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IllegalArgumentException("Não foi possível ler a imagem.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("O arquivo enviado não é uma imagem válida.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String formatoReal = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!formatoReal.equals(formatoEsperado)
                        && !(formatoEsperado.equals("jpeg") && formatoReal.equals("jpg"))) {
                    throw new IllegalArgumentException("O formato do arquivo não corresponde à imagem.");
                }
                int largura = reader.getWidth(0);
                int altura = reader.getHeight(0);
                if (largura <= 0 || altura <= 0 || largura > 4096 || altura > 4096
                        || (long) largura * altura > MAX_PIXELS) {
                    throw new IllegalArgumentException("A imagem excede as dimensões permitidas.");
                }
                BufferedImage imagem = reader.read(0);
                if (imagem == null) {
                    throw new IllegalArgumentException("O arquivo enviado não é uma imagem válida.");
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(imagem, formatoEsperado, output)) {
                    throw new IllegalArgumentException("Não foi possível processar a imagem.");
                }
                byte[] imagemValidada = output.toByteArray();
                if (imagemValidada.length > MAX_BYTES) {
                    throw new IllegalArgumentException("A imagem processada excede o limite de 2 MB.");
                }
                return new Conteudo(imagemValidada, mime);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Não foi possível processar a imagem.", exception);
        }
    }
}
