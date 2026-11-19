package br.com.clinicaveterinaria;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class CpfCriptografia {
    private static final String ENVIRONMENT_KEY = "CLINICA_CPF_KEY";
    private static final byte[] AAD = "clinica-veterinaria-cpf-v1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private CpfCriptografia() {
    }

    static String normalizar(String cpf) {
        if (cpf == null) {
            throw new IllegalArgumentException("Informe o CPF do tutor.");
        }

        String numeros = cpf.replaceAll("\\D", "");
        if (numeros.length() != 11 || numeros.matches("(\\d)\\1{10}")) {
            throw new IllegalArgumentException("Informe um CPF válido com 11 dígitos.");
        }
        return numeros;
    }

    static String criptografar(String cpf) throws GeneralSecurityException {
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, readKey(), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD);
        byte[] encrypted = cipher.doFinal(cpf.getBytes(StandardCharsets.UTF_8));

        ByteBuffer payload = ByteBuffer.allocate(nonce.length + encrypted.length);
        payload.put(nonce);
        payload.put(encrypted);
        return "v1:" + Base64.getEncoder().encodeToString(payload.array());
    }

    static boolean corresponde(String storedValue, String cpf) throws GeneralSecurityException {
        if (storedValue == null || !storedValue.startsWith("v1:")) {
            return false;
        }

        byte[] payload = Base64.getDecoder().decode(storedValue.substring(3));
        if (payload.length <= 12) {
            throw new GeneralSecurityException("Valor de CPF cifrado inválido.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] nonce = new byte[12];
        buffer.get(nonce);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, readKey(), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD);
        String decrypted = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        return decrypted.equals(cpf);
    }

    private static SecretKeySpec readKey() {
        String configuredKey = System.getenv(ENVIRONMENT_KEY);
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException(
                    "Configure a variável de ambiente CLINICA_CPF_KEY com uma chave Base64 de 32 bytes "
                            + "para armazenar CPF com segurança.");
        }

        byte[] key;
        try {
            key = Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("CLINICA_CPF_KEY deve conter uma chave Base64 válida.", exception);
        }
        if (key.length != 32) {
            throw new IllegalStateException("CLINICA_CPF_KEY deve decodificar para exatamente 32 bytes.");
        }
        return new SecretKeySpec(key, "AES");
    }
}
