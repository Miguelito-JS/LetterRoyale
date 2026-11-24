package br.com.letterroyale.casa.aparelhos;

import br.com.letterroyale.casa.TuyaService;
import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * =====================================================================
 *  CORTINA e AR-CONDICIONADO de verdade (pela nuvem Tuya, igual à lâmpada).
 *
 *  Sem ID cadastrado -> o aparelho continua SIMULADO (só banco + Sala ao vivo).
 *  Com ID cadastrado (Gerenciar -> Cortina e ar) -> o comando vai pro aparelho.
 *  A Tuya ainda sem chaves (TUYA_CLIENT_ID/SECRET) -> só aparece no console.
 * =====================================================================
 */
@Service
public class AparelhosService {
    private static final Logger log = LoggerFactory.getLogger(AparelhosService.class);
    private final Db db;
    private final TuyaService tuya;

    public AparelhosService(Db db, TuyaService tuya) {
        this.db = db;
        this.tuya = tuya;
    }

    /** Manda o estado do dispositivo (linha da tabela dispositivos) pro aparelho real. null = deu certo/simulado. */
    public String aplicar(Map<String, Object> d) {
        String tipo = String.valueOf(d.get("tipo"));
        if (!"cortinas".equals(tipo) && !"temperatura".equals(tipo)) return null;
        ComandosTuya.Destino destino;
        try {
            destino = ComandosTuya.destino((String) d.get("tuya_device_id"));
        } catch (IllegalArgumentException e) {
            return "Salvo, mas o ID do aparelho está errado: " + e.getMessage();
        }
        if (destino == null) return null;   // simulado
        String nome = "cortinas".equals(tipo) ? "cortina" : "ar-condicionado";
        try {
            if ("cortinas".equals(tipo)) {
                tuya.enviarComandos(destino.deviceId(), nome, ComandosTuya.cortina((String) d.get("estado"), codigos(destino.deviceId())));
            } else {
                boolean ligado = Json.verdade(d.get("ligado"));
                int temp = ((Number) d.get("valor")).intValue();
                if (destino.infravermelho()) {
                    tuya.comandoArInfravermelho(destino.hubId(), destino.controleId(), ComandosTuya.arInfravermelho(ligado, temp));
                } else {
                    tuya.enviarComandos(destino.deviceId(), nome, ComandosTuya.ar(ligado, temp, codigos(destino.deviceId()), escalaTemperatura(destino.deviceId())));
                }
            }
            return null;
        } catch (TuyaService.LampadaException e) {
            log.warn("{}: {}", nome, e.getMessage());
            return "Salvo, mas o aparelho (" + nome + ") não respondeu: " + e.getMessage();
        }
    }

    /** Relê do banco e manda (usado depois de cenas e atmosferas). */
    public String aplicarTipo(long uid, String tipo) {
        return db.um("SELECT * FROM dispositivos WHERE usuario_id = ? AND tipo = ?", uid, tipo).map(this::aplicar).orElse(null);
    }

    /** Gerenciar: guarda o ID do aparelho real (vazio = volta a ser simulado). */
    public void configurar(long uid, long dispositivoId, String deviceId) {
        Map<String, Object> d = db.um("SELECT * FROM dispositivos WHERE id = ? AND usuario_id = ?", dispositivoId, uid)
                .orElseThrow(() -> new ApiException(404, "Dispositivo não encontrado."));
        String valor = deviceId == null || deviceId.isBlank() ? null : deviceId.trim();
        if (valor != null && !"iluminacao".equals(d.get("tipo"))) {
            try {
                ComandosTuya.destino(valor);   // valida o formato
            } catch (IllegalArgumentException e) {
                throw new ApiException(400, e.getMessage());
            }
        }
        db.executar("UPDATE dispositivos SET tuya_device_id = ? WHERE id = ?", valor, dispositivoId);
    }

    /** Botão "Testar": reenvia o estado atual. Devolve um texto pra mostrar na tela. */
    public String testar(long uid, long dispositivoId) {
        Map<String, Object> d = db.um("SELECT * FROM dispositivos WHERE id = ? AND usuario_id = ?", dispositivoId, uid)
                .orElseThrow(() -> new ApiException(404, "Dispositivo não encontrado."));
        if (d.get("tuya_device_id") == null) return "Este aparelho está em simulação: cadastre o ID dele primeiro.";
        if (!tuya.configurado()) return "ID salvo, mas faltam as chaves da Tuya no servidor (veja o LAMPADA.md). Por enquanto o comando só aparece no console.";
        String aviso = aplicar(d);
        return aviso != null ? aviso : "Comando enviado! O aparelho respondeu.";
    }

    /** Códigos que o aparelho aceita (vazio se não deu pra perguntar). */
    private Set<String> codigos(String deviceId) {
        Set<String> s = new HashSet<>();
        if (deviceId == null) return s;
        try {
            JsonNode f = tuya.funcoesDoAparelho(deviceId);
            if (f != null) f.forEach(x -> s.add(x.path("code").asText()));
        } catch (Exception e) {
            log.warn("Não consegui ler as funções do aparelho {}: {}", deviceId, e.getMessage());
        }
        return s;
    }

    private int escalaTemperatura(String deviceId) {
        try {
            JsonNode f = tuya.funcoesDoAparelho(deviceId);
            if (f != null) for (JsonNode x : f) if ("temp_set".equals(x.path("code").asText())) return ComandosTuya.escala(x.path("values").asText());
        } catch (Exception ignorado) { /* usa 0 */ }
        return 0;
    }
}
