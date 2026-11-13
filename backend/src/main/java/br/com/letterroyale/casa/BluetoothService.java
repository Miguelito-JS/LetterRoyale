package br.com.letterroyale.casa;

import br.com.letterroyale.erro.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Painel "Gerenciar" da TV e Som: caixinhas e fones Bluetooth do computador onde o servidor roda.
 * Lista os pareados, procura novos por perto, pareia, conecta (e passa o som pra ele) e desconecta.
 * Depois de conectado, o slider de volume (SomService) já mexe nele, porque vira a saída padrão.
 *
 * Por baixo: resources/som/bluetooth.ps1, um PowerShell por comando (são ações raras e às vezes
 * demoradas: procurar leva ~30 s), que responde uma linha JSON.
 */
@Service
public class BluetoothService {
    private static final Logger log = LoggerFactory.getLogger(BluetoothService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final SomService som;
    private Path script;

    public BluetoothService(SomService som) {
        this.som = som;
    }

    /** Pareados (conectados ou não) */
    public JsonNode listar() {
        return rodar(15, "listar");
    }

    /** Aparelhos de áudio por perto ainda não pareados (~30 s) */
    public JsonNode procurar() {
        return rodar(60, "procurar");
    }

    /** Pareia e já tenta conectar o som */
    public void parear(String id, String mac) {
        rodar(60, "parear", id);
        if (mac != null && !mac.isBlank()) {
            try {
                conectar(mac);
            } catch (ApiException e) {
                log.info("🔊 Pareou, mas a conexão automática não rolou: {}", e.getMessage());   // o Windows costuma conectar sozinho
            }
        }
    }

    public void conectar(String mac) {
        rodar(30, "conectar", mac);
    }

    public void desconectar(String mac) {
        rodar(20, "desconectar", mac);
    }

    private synchronized JsonNode rodar(int segundos, String... args) {
        if (!som.disponivel()) throw new ApiException(400, "Bluetooth só funciona com o servidor rodando no Windows.");
        try {
            if (script == null) {
                script = Files.createTempFile("letterroyale-bluetooth", ".ps1");
                try (InputStream in = getClass().getResourceAsStream("/som/bluetooth.ps1")) {
                    Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
                }
                script.toFile().deleteOnExit();
            }
            List<String> cmd = new ArrayList<>(List.of("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toString()));
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            // lê a saída enquanto roda (se o buffer do processo enche, ele trava esperando)
            CompletableFuture<String> leitura = CompletableFuture.supplyAsync(() -> {
                try { return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8); } catch (Exception e) { return ""; }
            });
            if (!p.waitFor(segundos, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new ApiException(504, "O Bluetooth demorou demais para responder.");
            }
            String saida = leitura.get(5, TimeUnit.SECONDS).trim();
            String ultima = saida.substring(saida.lastIndexOf('\n') + 1);
            JsonNode r = json.readTree(ultima);
            if (!r.path("ok").asBoolean()) throw new ApiException(502, r.path("erro").asText("Falha no Bluetooth."));
            return r;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("🔊 Falha no Bluetooth ({}): {}", args[0], e.toString());
            throw new ApiException(502, "Não consegui falar com o Bluetooth do computador.");
        }
    }
}
