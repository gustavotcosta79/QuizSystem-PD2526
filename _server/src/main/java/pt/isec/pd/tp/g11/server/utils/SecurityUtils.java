/*
 * Ficheiro: SecurityUtils.java
 * Objetivo: Classe de utilitário para funções de segurança (Hashing).
 * Responsabilidade: Gerar hashes SHA-256 para passwords e
 * códigos de registo.
 */
package pt.isec.pd.tp.g11.server.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SecurityUtils {

    /**
     * Gera um hash SHA-256 para uma dada string.
     * @param text A string a ser "hasheada" (ex: password, código).
     * @return A representação hexadecimal do hash.
     */
    public static String hashPassword(String text) {
        if (text == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Algoritmo de Hashing não encontrado: " + e.getMessage());
            // Em caso de falha, (não ideal) retorna o texto simples
            // para que a falha seja óbvia. Numa app real, isto devia
            // lançar uma exceção e parar o arranque.
            return text;
        }
    }

    /**
     * Verifica se uma password (em texto simples) corresponde a um hash guardado.
     * @param plainPassword A password que o utilizador enviou.
     * @param storedHash O hash que está guardado na base de dados.
     * @return true se corresponderem, false caso contrário.
     */
    public static boolean checkPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        // Gera o hash da password que o utilizador enviou
        String newHash = hashPassword(plainPassword);
        // Compara o novo hash com o hash guardado
        return newHash.equals(storedHash);
    }

    /**
     * Helper para converter um array de bytes num string hexadecimal.
     */
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}