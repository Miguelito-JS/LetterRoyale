package br.com.letterroyale.casa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * =====================================================================
 *  Fala com a NUVEM DA TUYA, que controla a lâmpada Tramontina (e outras
 *  lâmpadas "white label": Positivo, i2go, Smarteck, Elgin...).
 *
 *  Fluxo:  site -> CasaController -> TuyaService -> nuvem Tuya -> lâmpada
 *
 *  MODO SIMULAÇÃO: se TUYA_CLIENT_ID / TUYA_SECRET / TUYA_DEVICE_ID não
 *  estiverem configurados, nada é enviado — só aparece no log (console)
 *  o que SERIA enviado. Assim o site funciona antes da lâmpada chegar.
 *
 *  Passo a passo para ligar de verdade: ver LAMPADA.md na raiz do projeto.
 * =====================================================================
 */
@Service
public class TuyaService {
    private static final Logger log = LoggerFactory.getLogger(TuyaService.class);
    private static final String SHA256_VAZIO = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final String clientId;
    private final String secret;
    private final String baseUrl;
    private final String deviceIdPadrao;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper json = new ObjectMapper();

    // Token de acesso (vale ~2h). Guardamos e renovamos quando vence.
    private String token;
    private long tokenExpiraEm;

    // Cada modelo de lâmpada usa nomes um pouco diferentes (bright_value ou bright_value_v2...).
    // Na 1ª vez perguntamos à Tuya quais comandos ESTA lâmpada aceita e guardamos aqui.
    private final Map<String, Funcoes> funcoesPorDevice = new HashMap<>();

    public TuyaService(@Value("${app.tuya.client-id:}") String clientId,
                       @Value("${app.tuya.secret:}") String secret,
                       @Value("${app.tuya.url:https://openapi.tuyaus.com}") String baseUrl,
                       @Value("${app.tuya.device-id:}") String deviceIdPadrao) {
        this.clientId = clientId.trim();
        this.secret = secret.trim();
        this.baseUrl = baseUrl.trim().replaceAll("/$", "");
        this.deviceIdPadrao = deviceIdPadrao.trim();
        log.info(configurado() ? "💡 Tuya configurada (lâmpada REAL)" : "💡 Tuya NÃO configurada -> lâmpada em MODO SIMULAÇÃO");
    }

    /** true = tem chaves da Tuya (manda de verdade). false = modo simulação. */
    public boolean configurado() {
        return !clientId.isEmpty() && !secret.isEmpty();
    }

    /** Qual lâmpada usar: a salva no banco para o usuário, ou a padrão da configuração */
    public String deviceId(String doBanco) {
        return (doBanco != null && !doBanco.isBlank()) ? doBanco.trim() : deviceIdPadrao;
    }

    // =================================================================
    //  COMANDOS "DE ALTO NÍVEL" (é isso que o CasaController chama)
    // =================================================================

    /** Liga/desliga e ajusta o brilho (0 a 100%) em luz branca. */
    public void luz(String deviceId, boolean ligada, int brilhoPct) {
        List<Map<String, Object>> cmds = new ArrayList<>();
        cmds.add(cmd("switch_led", ligada));
        if (ligada) {
            Funcoes f = funcoes(deviceId);
            cmds.add(cmd(f.codigoModo, "white"));
            cmds.add(cmd(f.codigoBrilho, f.brilho(brilhoPct)));
        }
        enviar(deviceId, cmds);
    }

    /** Luz branca com brilho + temperatura de cor (0 = bem amarelada/quente, 100 = bem branca/fria). */
    public void luzBranca(String deviceId, int brilhoPct, int temperaturaPct) {
        Funcoes f = funcoes(deviceId);
        enviar(deviceId, List.of(
                cmd("switch_led", true),
                cmd(f.codigoModo, "white"),
                cmd(f.codigoBrilho, f.brilho(brilhoPct)),
                cmd(f.codigoTemperatura, f.temperatura(temperaturaPct))));
    }

    /** Luz colorida. hex = "#ff8800". brilhoPct 0-100. */
    public void cor(String deviceId, String hex, int brilhoPct) {
        Funcoes f = funcoes(deviceId);
        float[] hsv = hexParaHsv(hex);
        Map<String, Object> valor;
        if (f.coresV2) {   // padrão novo: h 0-360, s 0-1000, v 0-1000
            valor = Map.of("h", Math.round(hsv[0]), "s", Math.round(hsv[1] * 1000),
                    "v", Math.max(10, Math.round(brilhoPct * 10f)));
        } else {           // padrão antigo: h 0-360, s 0-255, v 0-255
            valor = Map.of("h", Math.round(hsv[0]), "s", Math.round(hsv[1] * 255),
                    "v", Math.max(25, Math.round(brilhoPct * 2.55f)));
        }
        String valorJson;
        try {
            valorJson = json.writeValueAsString(valor);   // a Tuya quer a cor como TEXTO json
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        enviar(deviceId, List.of(cmd("switch_led", true), cmd(f.codigoModo, "colour"), cmd(f.codigoCor, valorJson)));
    }

    /** Lê o estado atual da lâmpada (ligada? brilho?). null no modo simulação. */
    public JsonNode status(String deviceId) {
        if (!configurado() || deviceId.isEmpty()) return null;
        return chamar("GET", "/v1.0/iot-03/devices/" + deviceId + "/status", null).path("result");
    }

    /** Lista as lâmpadas/aparelhos da conta Smart Life vinculada (útil pra descobrir o device_id). */
    public JsonNode dispositivosDaConta() {
        if (!configurado()) return null;
        return chamar("GET", "/v1.0/iot-01/associated-users/devices?size=50", null).path("result");
    }

    // =================================================================
    //  DETALHES (normalmente ninguém precisa mexer daqui pra baixo)
    // =================================================================

    private static Map<String, Object> cmd(String code, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("value", value);
        return m;
    }

    /** POST /v1.0/iot-03/devices/{id}/commands */
    private void enviar(String deviceId, List<Map<String, Object>> comandos) {
        if (!configurado() || deviceId.isEmpty()) {
            log.info("💡 [SIMULAÇÃO] lâmpada <- {}", comandos);
            return;
        }
        try {
            String corpo = json.writeValueAsString(Map.of("commands", comandos));
            JsonNode r = chamar("POST", "/v1.0/iot-03/devices/" + deviceId + "/commands", corpo);
            if (!r.path("success").asBoolean(false)) {
                throw new LampadaException("A Tuya recusou o comando: " + r.path("msg").asText("?"));
            }
            log.info("💡 lâmpada <- {}", comandos);
        } catch (LampadaException e) {
            throw e;
        } catch (Exception e) {
            throw new LampadaException("Não consegui falar com a lâmpada: " + e.getMessage());
        }
    }

    /** Pergunta à Tuya quais comandos a lâmpada aceita (e os limites de cada um). */
    private synchronized Funcoes funcoes(String deviceId) {
        if (!configurado() || deviceId.isEmpty()) return Funcoes.PADRAO;
        return funcoesPorDevice.computeIfAbsent(deviceId, id -> {
            try {
                JsonNode lista = chamar("GET", "/v1.0/iot-03/devices/" + id + "/functions", null)
                        .path("result").path("functions");
                Funcoes f = Funcoes.de(lista, json);
                log.info("💡 Lâmpada {} usa: brilho={} [{}-{}], temp={}, cor={}", id, f.codigoBrilho, f.brilhoMin, f.brilhoMax, f.codigoTemperatura, f.codigoCor);
                return f;
            } catch (Exception e) {
                log.warn("Não consegui ler as funções da lâmpada, usando padrão v2", e);
                return Funcoes.PADRAO;
            }
        });
    }

    /** Pega (ou renova) o token de acesso: GET /v1.0/token?grant_type=1 */
    private synchronized String token() throws Exception {
        if (token != null && System.currentTimeMillis() < tokenExpiraEm) return token;
        JsonNode r = requisicao("GET", "/v1.0/token?grant_type=1", null, false);
        if (!r.path("success").asBoolean(false)) {
            throw new LampadaException("Tuya recusou as chaves (client_id/secret ou região errados): " + r.path("msg").asText());
        }
        token = r.path("result").path("access_token").asText();
        long segundos = r.path("result").path("expire_time").asLong(3600);
        tokenExpiraEm = System.currentTimeMillis() + (segundos - 60) * 1000;   // renova 1 min antes
        return token;
    }

    private JsonNode chamar(String metodo, String caminho, String corpo) {
        try {
            JsonNode r = requisicao(metodo, caminho, corpo, true);
            // Token venceu no meio do caminho? Renova e tenta 1x de novo.
            if (r.path("code").asInt() == 1010) {
                token = null;
                r = requisicao(metodo, caminho, corpo, true);
            }
            return r;
        } catch (LampadaException e) {
            throw e;
        } catch (Exception e) {
            throw new LampadaException("Sem conexão com a nuvem Tuya: " + e.getMessage());
        }
    }

    /**
     * Monta a requisição com a ASSINATURA exigida pela Tuya:
     *   stringToSign = METODO \n SHA256(corpo) \n (headers extras, vazio) \n caminho
     *   token:   sign = HMAC-SHA256(client_id + t + nonce + stringToSign, secret) em MAIÚSCULAS
     *   demais:  sign = HMAC-SHA256(client_id + access_token + t + nonce + stringToSign, secret)
     */
    private JsonNode requisicao(String metodo, String caminho, String corpo, boolean comToken) throws Exception {
        String t = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String accessToken = comToken ? token() : "";
        String hashCorpo = (corpo == null || corpo.isEmpty()) ? SHA256_VAZIO : sha256(corpo);
        String stringToSign = metodo + "\n" + hashCorpo + "\n" + "" + "\n" + caminho;
        String sign = assinar(clientId + accessToken + t + nonce + stringToSign, secret);

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + caminho))
                .timeout(Duration.ofSeconds(10))
                .header("client_id", clientId)
                .header("sign", sign)
                .header("t", t)
                .header("nonce", nonce)
                .header("sign_method", "HMAC-SHA256")
                .header("Content-Type", "application/json");
        if (comToken) req.header("access_token", accessToken);
        req.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8));

        HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        return json.readTree(resp.body());
    }

    static String sha256(String texto) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(h);
    }

    static String assinar(String texto, String chave) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8))).toUpperCase();
    }

    /** "#ff8800" -> [matiz 0-360, saturação 0-1, valor 0-1] */
    static float[] hexParaHsv(String hex) {
        String h = hex == null ? "ffffff" : hex.replace("#", "");
        if (h.length() != 6) h = "ffffff";
        int rgb = Integer.parseInt(h, 16);
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, null);
        return new float[]{hsb[0] * 360f, hsb[1], hsb[2]};
    }

    /** Erro ao falar com a lâmpada (o site continua funcionando, só mostra um aviso). */
    public static class LampadaException extends RuntimeException {
        public LampadaException(String msg) {
            super(msg);
        }
    }

    /** Quais "códigos" a lâmpada usa e os limites de cada um. */
    static class Funcoes {
        String codigoModo = "work_mode";
        String codigoBrilho = "bright_value_v2";
        String codigoTemperatura = "temp_value_v2";
        String codigoCor = "colour_data_v2";
        int brilhoMin = 10, brilhoMax = 1000, tempMin = 0, tempMax = 1000;
        boolean coresV2 = true;

        static final Funcoes PADRAO = new Funcoes();

        int brilho(int pct) {
            int p = Math.max(0, Math.min(100, pct));
            return brilhoMin + Math.round((brilhoMax - brilhoMin) * p / 100f);
        }

        int temperatura(int pct) {
            int p = Math.max(0, Math.min(100, pct));
            return tempMin + Math.round((tempMax - tempMin) * p / 100f);
        }

        static Funcoes de(JsonNode lista, ObjectMapper json) throws Exception {
            Funcoes f = new Funcoes();
            Set<String> codigos = new HashSet<>();
            for (JsonNode fn : lista) {
                String code = fn.path("code").asText();
                codigos.add(code);
                JsonNode faixa = json.readTree(fn.path("values").asText("{}"));
                if (code.startsWith("bright_value")) {
                    f.codigoBrilho = code;
                    f.brilhoMin = faixa.path("min").asInt(f.brilhoMin);
                    f.brilhoMax = faixa.path("max").asInt(f.brilhoMax);
                } else if (code.startsWith("temp_value")) {
                    f.codigoTemperatura = code;
                    f.tempMin = faixa.path("min").asInt(f.tempMin);
                    f.tempMax = faixa.path("max").asInt(f.tempMax);
                }
            }
            if (!codigos.contains("colour_data_v2") && codigos.contains("colour_data")) {
                f.codigoCor = "colour_data";
                f.coresV2 = false;
            }
            if (!codigos.contains("work_mode") && codigos.contains("work_mode_v2")) f.codigoModo = "work_mode_v2";
            return f;
        }
    }
}
