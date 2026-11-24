package br.com.letterroyale.casa.controle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * =====================================================================
 *  HTTPS NA REDE DE CASA (pro microfone do celular funcionar)
 *
 *  O Chrome do celular só libera o microfone em site seguro (https).
 *  Pelo IP do Wi-Fi (http://192.168...) ele bloqueia. Solução sem programa extra:
 *
 *   1) Na 1ª vez, cria uma "autoridade certificadora" (CA, certificate authority)
 *      SÓ DESTE PC: segredos/https/ca.p12 + letterroyale-ca.crt (o público).
 *   2) Toda vez que o servidor sobe, confere os IPs do PC. Se mudaram (outra rede,
 *      a feira...), gera um certificado novo do servidor, assinado pela CA,
 *      valendo pra localhost + os IPs atuais.
 *   3) O celular instala o letterroyale-ca.crt UMA vez e passa a confiar
 *      em https://IP:8443 em qualquer rede.
 *
 *  Usa o keytool que já vem no Java (nada pra instalar). A pasta segredos/
 *  é ignorada pelo Git: a chave da CA nunca vai pro GitHub.
 * =====================================================================
 */
public final class CertificadoLocal {
    private static final Logger log = LoggerFactory.getLogger(CertificadoLocal.class);

    public static final String ALIAS = "servidor";
    public static final String ARQUIVO_CA = "letterroyale-ca.crt";

    /** Resultado: onde está o keystore do servidor e a senha. */
    public record Pronto(Path keystore, String senha, Path certificadoCa) { }

    private CertificadoLocal() {
    }

    /**
     * Lista "SAN" (subject alternative names, nomes alternativos) do certificado:
     * localhost + 127.0.0.1 + cada IP da rede, sem repetir. Ex.: "dns:localhost,ip:127.0.0.1,ip:192.168.0.15"
     */
    public static String nomesAlternativos(List<String> ips) {
        Set<String> nomes = new LinkedHashSet<>(List.of("dns:localhost", "ip:127.0.0.1"));
        for (String ip : ips) {
            if (ip != null && ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) nomes.add("ip:" + ip);
        }
        return String.join(",", nomes);
    }

    /** Pasta segredos/https (rodando de backend/ ou da raiz do projeto). */
    public static Path pasta() {
        Path base = Files.isDirectory(Path.of("..", "segredos")) ? Path.of("..", "segredos")
                : Files.isDirectory(Path.of("..", "frontend")) ? Path.of("..", "segredos")
                : Path.of("segredos");
        return base.resolve("https").toAbsolutePath().normalize();
    }

    /** Garante a CA e um certificado do servidor com os IPs atuais. Devolve null se não deu (o site segue só em http). */
    public static Pronto preparar(List<String> ips) {
        try {
            Path pasta = pasta();
            Files.createDirectories(pasta);
            String senha = senha(pasta.resolve("senha.txt"));
            Path ca = pasta.resolve("ca.p12"), caCrt = pasta.resolve(ARQUIVO_CA);
            Path servidor = pasta.resolve("servidor.p12"), sanArq = pasta.resolve("servidor.san");

            if (!Files.exists(ca) || !Files.exists(caCrt)) {
                Files.deleteIfExists(ca);
                Files.deleteIfExists(servidor);
                keytool("-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
                        "-dname", "CN=LetterRoyale CA local, O=LetterRoyale",
                        "-ext", "bc:c", "-ext", "KeyUsage=keyCertSign,cRLSign",
                        "-keystore", ca.toString(), "-storetype", "PKCS12", "-storepass", senha, "-keypass", senha);
                keytool("-exportcert", "-alias", "ca", "-keystore", ca.toString(), "-storepass", senha, "-rfc", "-file", caCrt.toString());
                log.info("Criei a CA local do LetterRoyale em {}", pasta);
            }

            String san = nomesAlternativos(ips);
            boolean mesmoSan = Files.exists(sanArq) && Files.readString(sanArq, StandardCharsets.UTF_8).trim().equals(san);
            boolean novo = Files.exists(servidor) && Files.getLastModifiedTime(servidor).toMillis() > System.currentTimeMillis() - 300L * 24 * 3600 * 1000;
            if (!(mesmoSan && novo)) {
                Path csr = pasta.resolve("servidor.csr"), crt = pasta.resolve("servidor.crt");
                Files.deleteIfExists(servidor);
                keytool("-genkeypair", "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "2048", "-validity", "397",
                        "-dname", "CN=LetterRoyale", "-keystore", servidor.toString(), "-storetype", "PKCS12",
                        "-storepass", senha, "-keypass", senha);
                keytool("-certreq", "-alias", ALIAS, "-keystore", servidor.toString(), "-storepass", senha, "-file", csr.toString());
                keytool("-gencert", "-alias", "ca", "-keystore", ca.toString(), "-storepass", senha,
                        "-infile", csr.toString(), "-outfile", crt.toString(), "-rfc", "-validity", "397",
                        "-ext", "SAN=" + san, "-ext", "EKU=serverAuth", "-ext", "KeyUsage=digitalSignature,keyEncipherment");
                keytool("-importcert", "-alias", "ca", "-file", caCrt.toString(), "-keystore", servidor.toString(),
                        "-storepass", senha, "-noprompt");
                keytool("-importcert", "-alias", ALIAS, "-file", crt.toString(), "-keystore", servidor.toString(),
                        "-storepass", senha, "-noprompt");
                Files.writeString(sanArq, san, StandardCharsets.UTF_8);
                Files.deleteIfExists(csr);
                log.info("Certificado HTTPS novo pra: {}", san);
            }
            return new Pronto(servidor, senha, caCrt);
        } catch (Exception e) {
            log.warn("Não consegui preparar o HTTPS da rede local ({}). O site continua em http://localhost:8080.", e.getMessage());
            return null;
        }
    }

    private static String senha(Path arquivo) throws Exception {
        if (Files.exists(arquivo)) {
            String s = Files.readString(arquivo, StandardCharsets.UTF_8).trim();
            if (s.length() >= 12) return s;
        }
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 24; i++) sb.append(letras.charAt(r.nextInt(letras.length())));
        Files.writeString(arquivo, sb.toString(), StandardCharsets.UTF_8);
        return sb.toString();
    }

    /** Roda o keytool do próprio Java que está rodando o servidor. */
    private static void keytool(String... args) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path exe = Path.of(System.getProperty("java.home"), "bin", windows ? "keytool.exe" : "keytool");
        List<String> cmd = new ArrayList<>();
        cmd.add(exe.toString());
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String saida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IllegalStateException("keytool " + args[0] + " falhou: " + saida.trim());
    }
}
