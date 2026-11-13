package br.com.letterroyale.casa;

import br.com.letterroyale.erro.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * "TV e Som" de verdade: muda o volume da SAÍDA DE SOM PADRÃO do Windows onde o servidor roda.
 * Se uma caixinha/fone Bluetooth estiver conectado e escolhido como saída, é ele que muda.
 *
 * Por baixo: um PowerShell (resources/som/volume.ps1) fica aberto e recebe um comando por linha
 * ("get", "set 45", "mute 1"), respondendo uma linha JSON. Abrir um PowerShell por comando
 * levaria ~1 s; deixando aberto, o slider responde na hora.
 *
 * Fora do Windows (ou se der erro) fica em SIMULAÇÃO: só salva no banco, como antes.
 */
@Service
public class SomService {
    private static final Logger log = LoggerFactory.getLogger(SomService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");

    private Process ps;
    private PrintWriter entrada;
    private BufferedReader saida;

    public SomService() {
        log.info(windows ? "🔊 TV e Som: controla o volume REAL do Windows (saída padrão)" : "🔊 TV e Som em MODO SIMULAÇÃO (não é Windows)");
        if (windows) CompletableFuture.runAsync(this::estado);   // já abre o PowerShell (a 1ª resposta leva ~3 s)
    }

    /** true = dá pra mexer no volume de verdade */
    public boolean disponivel() {
        return windows;
    }

    /** Estado atual: { ok, nome, volume, mudo, bluetooth } ou null se não deu */
    public JsonNode estado() {
        return comando("get");
    }

    /** Liga (tira do mudo) com o volume em %, ou desliga (mudo). Devolve null se deu certo, ou o motivo. */
    public String aplicar(boolean ligado, int volume) {
        if (!windows) return null;
        JsonNode r = ligado ? comando("set " + Math.max(0, Math.min(100, volume))) : comando("mute 1");
        if (r == null) return "Salvo, mas não consegui mexer no volume do computador.";
        return r.path("ok").asBoolean() ? null : "Salvo, mas o volume não mudou: " + r.path("erro").asText();
    }

    // ---- Celular tocando NO computador (o PC vira a caixinha Bluetooth do celular) ----

    /** Celulares pareados que podem mandar som pra cá: [{ id, nome, tocando }] */
    public JsonNode celulares() {
        return exigir(comando("celulares", 20)).path("celulares");
    }

    /** O som do celular passa a sair no computador (e o slider de volume controla) */
    public void tocarCelular(String id) {
        exigir(comando("tocar " + id, 35));
    }

    public void pararCelular(String id) {
        exigir(comando("parar " + id, 10));
    }

    private JsonNode exigir(JsonNode r) {
        if (r == null) throw new ApiException(502, "Não consegui falar com o som do computador.");
        if (!r.path("ok").asBoolean()) throw new ApiException(502, r.path("erro").asText("Falha no som do computador."));
        return r;
    }

    private JsonNode comando(String cmd) {
        return comando(cmd, 5);
    }

    private synchronized JsonNode comando(String cmd, int segundos) {
        if (!windows) return null;
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            try {
                boolean novo = ps == null || !ps.isAlive();
                if (novo) abrir();
                entrada.println(cmd);
                // Espera a resposta (a 1ª vez demora uns segundos: o PowerShell compila o C#)
                String linha = CompletableFuture.supplyAsync(() -> {
                    try { return saida.readLine(); } catch (Exception e) { return null; }
                }).get(novo ? segundos + 15 : segundos, TimeUnit.SECONDS);
                if (linha != null) return json.readTree(linha);
            } catch (Exception e) {
                log.warn("🔊 Falha ao falar com o PowerShell do volume: {}", e.toString());
            }
            fechar();   // travou ou morreu -> abre de novo e tenta mais uma vez
        }
        return null;
    }

    private void abrir() throws Exception {
        Path script = Files.createTempFile("letterroyale-volume", ".ps1");
        try (InputStream in = getClass().getResourceAsStream("/som/volume.ps1")) {
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }
        script.toFile().deleteOnExit();
        ps = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        entrada = new PrintWriter(ps.getOutputStream(), true, StandardCharsets.UTF_8);
        saida = new BufferedReader(new InputStreamReader(ps.getInputStream(), StandardCharsets.UTF_8));
    }

    @PreDestroy
    public synchronized void fechar() {
        if (ps != null) ps.destroyForcibly();
        ps = null;
    }
}
