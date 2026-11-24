package br.com.letterroyale.casa.aparelhos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * =====================================================================
 *  TRADUTOR: estado da tela (cortinas "fechadas", ar 21°C) -> comandos
 *  que a nuvem Tuya entende. Vale para QUALQUER aparelho compatível com
 *  o app Smart Life / Tuya Smart (Tramontina, Positivo, Elgin, i2go...).
 *
 *  Cortina inteligente (categoria Tuya "cl"):
 *     control = open | close | stop          percent_control = 0..100
 *  Ar-condicionado com Wi-Fi (categoria "kt"):
 *     switch = true/false                     temp_set = 16..30 (às vezes ×10)
 *  Ar-condicionado comum + controle universal IR (hub infravermelho Tuya):
 *     power 1/0, mode 0 = gelar, temp 16..30, wind 0 = automático
 *
 *  Como o aparelho é guardado (coluna dispositivos.tuya_device_id):
 *     "eb12ab34cd..."               -> aparelho Tuya com Wi-Fi
 *     "ir:<id do hub>:<id do ar>"   -> ar comum controlado pelo hub IR
 *
 *  Classe sem Spring e sem internet: testada com JUnit puro.
 * =====================================================================
 */
public final class ComandosTuya {

    /** Para onde mandar: aparelho Wi-Fi comum ou ar pelo hub infravermelho. */
    public record Destino(boolean infravermelho, String deviceId, String hubId, String controleId) {
    }

    private static final Pattern ESCALA = Pattern.compile("\"scale\"\\s*:\\s*(\\d+)");

    private ComandosTuya() {
    }

    /** null = aparelho simulado (sem ID cadastrado). */
    public static Destino destino(String configurado) {
        if (configurado == null || configurado.isBlank()) return null;
        String c = configurado.trim();
        if (c.startsWith("ir:")) {
            String[] p = c.split(":");
            if (p.length != 3 || p[1].isBlank() || p[2].isBlank()) {
                throw new IllegalArgumentException("Ar por infravermelho: use ir:<id do hub>:<id do controle do ar>");
            }
            return new Destino(true, null, p[1].trim(), p[2].trim());
        }
        if (c.contains(" ") || c.length() < 6) throw new IllegalArgumentException("ID de aparelho Tuya inválido: " + c);
        return new Destino(false, c, null, null);
    }

    /** Cortinas: abertas | meio | fechadas. "codigos" = o que o aparelho aceita (vazio = não sabemos). */
    public static List<Map<String, Object>> cortina(String estado, Set<String> codigos) {
        String e = estado == null ? "fechadas" : estado;
        boolean temPorcentagem = codigos != null && codigos.contains("percent_control");
        return switch (e) {
            case "abertas" -> List.of(cmd("control", "open"));
            case "meio" -> temPorcentagem ? List.of(cmd("percent_control", 50)) : List.of(cmd("control", "stop"));
            default -> List.of(cmd("control", "close"));
        };
    }

    /** Ar com Wi-Fi Tuya. escala = casas decimais do temp_set (0 -> 21, 1 -> 210). */
    public static List<Map<String, Object>> ar(boolean ligado, int tempC, Set<String> codigos, int escala) {
        String liga = codigos != null && !codigos.contains("switch") && codigos.contains("power") ? "power" : "switch";
        List<Map<String, Object>> cmds = new ArrayList<>();
        cmds.add(cmd(liga, ligado));
        if (ligado) cmds.add(cmd("temp_set", limitarTemperatura(tempC) * (int) Math.pow(10, Math.max(0, escala))));
        return cmds;
    }

    /** Ar comum pelo hub infravermelho (corpo do POST .../air-conditioners/{id}/scenes/command). */
    public static Map<String, Object> arInfravermelho(boolean ligado, int tempC) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("power", ligado ? 1 : 0);
        m.put("mode", 0);    // 0 = gelar
        m.put("temp", limitarTemperatura(tempC));
        m.put("wind", 0);    // 0 = automático
        return m;
    }

    /** Lê a escala de "values" da Tuya, ex.: {"min":160,"max":300,"scale":1,"step":5} -> 1. */
    public static int escala(String valuesJson) {
        if (valuesJson == null) return 0;
        Matcher m = ESCALA.matcher(valuesJson);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    static int limitarTemperatura(int c) {
        return Math.max(16, Math.min(30, c));
    }

    private static Map<String, Object> cmd(String code, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("value", value);
        return m;
    }
}
