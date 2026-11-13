package br.com.letterroyale.controller;

import br.com.letterroyale.casa.BluetoothService;
import br.com.letterroyale.casa.CasaService;
import br.com.letterroyale.casa.CinemaService;
import br.com.letterroyale.casa.SomService;
import br.com.letterroyale.casa.TuyaService;
import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.seguranca.Auth;
import br.com.letterroyale.tmdb.TmdbService;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Tela Smart Home + card "Minha casa" do Início.
 * A ILUMINAÇÃO é real (lâmpada Tuya/Tramontina via CasaService -> TuyaService).
 * A TV E SOM é real (volume do Windows / caixinha Bluetooth via SomService).
 * Cortinas e temperatura ainda são simuladas (só banco).
 */
@RestController
@RequestMapping("/api/casa")
public class CasaController {
    private final Db db;
    private final TmdbService tmdb;
    private final CasaService casa;
    private final TuyaService tuya;
    private final CinemaService cinema;
    private final SomService som;
    private final BluetoothService bluetooth;

    public CasaController(Db db, TmdbService tmdb, CasaService casa, TuyaService tuya, CinemaService cinema, SomService som, BluetoothService bluetooth) {
        this.db = db;
        this.tmdb = tmdb;
        this.casa = casa;
        this.tuya = tuya;
        this.cinema = cinema;
        this.som = som;
        this.bluetooth = bluetooth;
    }

    /** Resposta padrão: { ok: true } ou { ok: true, aviso: "a lâmpada não respondeu..." } */
    private static Map<String, Object> ok(String aviso) {
        Map<String, Object> r = Json.obj("ok", true);
        if (aviso != null) r.put("aviso", aviso);
        return r;
    }

    /** GET /api/casa -> dispositivos + cenas + próxima sessão + modo da lâmpada */
    @GetMapping
    public Map<String, Object> casa(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        List<Map<String, Object>> dispositivos = db.lista("SELECT * FROM dispositivos WHERE usuario_id = ? ORDER BY id", uid);
        dispositivos.forEach(d -> d.put("ligado", Json.verdade(d.get("ligado"))));
        // TV e Som: o volume pode ter mudado no próprio computador/caixinha -> a tela mostra o valor REAL
        Map<String, Object> somInfo = Json.obj("modo", "simulacao");
        JsonNode saida = som.disponivel() ? som.estado() : null;
        if (saida != null && saida.path("ok").asBoolean()) {
            somInfo = Json.obj("modo", "real", "nome", saida.path("nome").asText(), "bluetooth", saida.path("bluetooth").asBoolean(),
                    "celulares_tocando", saida.path("celulares_tocando"));
            int volume = saida.path("volume").asInt();
            boolean ligado = !saida.path("mudo").asBoolean();
            db.executar("UPDATE dispositivos SET valor = ?, ligado = ? WHERE usuario_id = ? AND tipo = 'tv_som'", volume, ligado, uid);
            dispositivos.stream().filter(d -> "tv_som".equals(d.get("tipo"))).forEach(d -> { d.put("valor", volume); d.put("ligado", ligado); });
        }
        List<Map<String, Object>> cenas = db.lista("SELECT * FROM cenas WHERE usuario_id = ? ORDER BY id", uid);
        cenas.forEach(c -> c.put("ativa", Json.verdade(c.get("ativa"))));

        Map<String, Object> proxima = db.um("""
                SELECT s.id AS sessao_id, s.data_hora, ce.nome AS cena, c.* FROM sessoes_agendadas s
                  JOIN filmes_cache c ON c.tmdb_id = s.tmdb_id
                  LEFT JOIN cenas ce ON ce.id = s.cena_id
                 WHERE s.usuario_id = ? AND s.data_hora >= NOW() ORDER BY s.data_hora LIMIT 1
                """, uid).map(s -> Json.obj("id", s.get("sessao_id"), "data_hora", s.get("data_hora"),
                "cena", s.get("cena"), "filme", TmdbService.doCache(s))).orElse(null);

        return Json.obj("dispositivos", dispositivos, "cenas", cenas, "proxima_sessao", proxima, "fila", cinema.fila(uid),
                "lampada", Json.obj("modo", tuya.configurado() ? "real" : "simulacao"), "som", somInfo,
                "cinema", cinema.estado(uid));
    }

    /** PATCH /api/casa/dispositivos/{id} { ligado?, valor?, estado? } -> sliders e botões */
    @PatchMapping("/dispositivos/{id}")
    public Map<String, Object> dispositivo(@PathVariable long id, @RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Map<String, Object> d = db.um("SELECT * FROM dispositivos WHERE id = ? AND usuario_id = ?", id, uid)
                .orElseThrow(() -> new ApiException(404, "Dispositivo não encontrado."));

        Boolean ligado = Json.bool(corpo, "ligado");
        Long valor = Json.numero(corpo, "valor");
        d.put("ligado", ligado != null ? ligado : Json.verdade(d.get("ligado")));
        d.put("valor", valor != null ? valor : d.get("valor"));
        d.put("estado", Json.texto(corpo, "estado", (String) d.get("estado")));

        db.executar("UPDATE dispositivos SET ligado = ?, valor = ?, estado = ? WHERE id = ?",
                d.get("ligado"), d.get("valor"), d.get("estado"), id);

        boolean luz = "iluminacao".equals(d.get("tipo"));
        if (luz && Json.verdade(d.get("ligado")) && cinema.reaplicarSeAtivo(uid)) {
            return ok(null);   // Modo Cinema continua; só muda o brilho das cores
        }
        if (luz && !Json.verdade(d.get("ligado"))) cinema.parar(uid);   // apagou a luz -> sai do Modo Cinema
        // Mexeu na mão -> nenhuma cena fica "ativa"
        db.executar("UPDATE cenas SET ativa = FALSE WHERE usuario_id = ?", uid);
        return ok(casa.aplicarDispositivo(uid, d));
    }

    /** POST /api/casa/cenas/{id}/ativar -> "Modo Cinema", "Maratona"... */
    @PostMapping("/cenas/{id}/ativar")
    @Transactional
    public Map<String, Object> ativarCena(@PathVariable long id, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        String nome = db.um("SELECT nome FROM cenas WHERE id = ? AND usuario_id = ?", id, uid)
                .map(c -> (String) c.get("nome")).orElseThrow(() -> new ApiException(404, "Cena não encontrada."));
        if ("Modo Cinema".equals(nome)) {   // Modo Cinema = cores do pôster (acompanha a navegação)
            cinema.iniciar(uid, null, "navegando", "normal");
            return ok(null);
        }
        return ok(casa.aplicarCena(uid, id));
    }

    /** POST /api/casa/cenas/desativar -> botão "Desativar" */
    @PostMapping("/cenas/desativar")
    public Map<String, Object> desativar(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        cinema.parar(uid);
        db.executar("UPDATE cenas SET ativa = FALSE WHERE usuario_id = ?", uid);
        return ok(null);
    }

    // =================================================================
    //  MODO CINEMA (cores do pôster) — ver CinemaService
    // =================================================================

    /** GET /api/casa/cinema -> está ligado? qual filme? quais cores? o filme acabou? */
    @GetMapping("/cinema")
    public Map<String, Object> cinemaEstado(HttpServletRequest req) {
        return cinema.estado(Auth.exigir(req).id());
    }

    /** POST /api/casa/cinema { tmdb_id?, modo: navegando|assistindo, ritmo: calmo|normal|festa } -> liga */
    @PostMapping("/cinema")
    public Map<String, Object> cinemaIniciar(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Long temp = Json.numero(corpo, "temporada"), ep = Json.numero(corpo, "episodio");   // só pra série
        cinema.iniciar(uid, Json.numero(corpo, "tmdb_id"), Json.texto(corpo, "modo", "navegando"), Json.texto(corpo, "ritmo", "normal"),
                temp == null ? null : temp.intValue(), ep == null ? null : ep.intValue());
        return cinema.estado(uid);
    }

    /** POST /api/casa/cinema/foco { tmdb_id } -> a pessoa abriu a página desse filme */
    @PostMapping("/cinema/foco")
    public Map<String, Object> cinemaFoco(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        Long tmdbId = Json.numero(corpo, "tmdb_id");
        if (tmdbId == null) throw new ApiException(400, "Filme inválido.");
        return Json.obj("aplicado", cinema.foco(Auth.exigir(req).id(), tmdbId));
    }

    /** PATCH /api/casa/cinema { ritmo } -> Calmo / Normal / Festa */
    @PatchMapping("/cinema")
    public Map<String, Object> cinemaRitmo(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        cinema.mudarRitmo(uid, Json.texto(corpo, "ritmo", "normal"));
        return cinema.estado(uid);
    }

    /** POST /api/casa/cinema/terminar -> "Encerrar filme agora" (pra apresentação) */
    @PostMapping("/cinema/terminar")
    public Map<String, Object> cinemaTerminar(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        cinema.terminarFilme(uid);
        return cinema.estado(uid);
    }

    /** DELETE /api/casa/cinema -> desliga o Modo Cinema / limpa o aviso de "filme acabou" */
    @DeleteMapping("/cinema")
    public Map<String, Object> cinemaParar(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        cinema.limparTerminado(uid);
        cinema.parar(uid);
        return ok(null);
    }

    // ---------- Fila do Modo Cinema ----------

    /** GET /api/casa/fila -> filmes na fila, na ordem */
    @GetMapping("/fila")
    public List<Map<String, Object>> fila(HttpServletRequest req) {
        return cinema.fila(Auth.exigir(req).id());
    }

    /** POST /api/casa/fila { tmdb_id } -> "Colocar na fila" (na página do filme) */
    @PostMapping("/fila")
    public List<Map<String, Object>> entrarNaFila(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Long tmdbId = Json.numero(corpo, "tmdb_id");
        if (tmdbId == null) throw new ApiException(400, "Filme inválido.");
        Long temp = Json.numero(corpo, "temporada"), ep = Json.numero(corpo, "episodio");   // só pra série
        cinema.entrarNaFila(uid, tmdbId, temp == null ? 0 : temp.intValue(), ep == null ? 0 : ep.intValue());
        return cinema.fila(uid);
    }

    /** DELETE /api/casa/fila/{id} -> tira da fila */
    @DeleteMapping("/fila/{id}")
    public List<Map<String, Object>> sairDaFila(@PathVariable long id, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        cinema.sairDaFila(uid, id);
        return cinema.fila(uid);
    }

    /** POST /api/casa/fila/{id}/subir -> sobe uma posição */
    @PostMapping("/fila/{id}/subir")
    public List<Map<String, Object>> subirNaFila(@PathVariable long id, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        cinema.subirNaFila(uid, id);
        return cinema.fila(uid);
    }

    /** POST /api/casa/fila/proximo -> começa o 1º da fila (no fim do filme ou na hora que quiser) */
    @PostMapping("/fila/proximo")
    public Map<String, Object> assistirProximo(HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        cinema.assistirProximo(uid);
        return cinema.estado(uid);
    }

    /** POST /api/casa/sessoes { tmdb_id, data_hora, cena_id } -> agendar "Próxima sessão" */
    @PostMapping("/sessoes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> agendar(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Long tmdbId = Json.numero(corpo, "tmdb_id");
        String dataHora = Json.texto(corpo, "data_hora", null);   // "2026-11-13 20:00:00"
        if (tmdbId == null || dataHora == null) throw new ApiException(400, "Informe filme e horário.");
        tmdb.garantirNoCache(tmdbId);
        long id = db.inserir("INSERT INTO sessoes_agendadas (usuario_id, tmdb_id, data_hora, cena_id) VALUES (?, ?, ?, ?)",
                uid, tmdbId, dataHora.replace('T', ' '), Json.numero(corpo, "cena_id"));
        return Json.obj("id", id);
    }

    // =================================================================
    //  LÂMPADA (para testar e configurar quando ela chegar)
    // =================================================================

    /** GET /api/casa/lampada -> modo (real/simulação), qual device_id e o estado atual */
    @GetMapping("/lampada")
    public Map<String, Object> lampada(HttpServletRequest req) {
        String deviceId = casa.lampadaDo(Auth.exigir(req).id());
        Map<String, Object> r = Json.obj("modo", tuya.configurado() ? "real" : "simulacao", "device_id", deviceId);
        try {
            r.put("status", tuya.status(deviceId));
        } catch (TuyaService.LampadaException e) {
            r.put("erro", e.getMessage());
        }
        return r;
    }

    /** GET /api/casa/lampada/dispositivos -> lista os aparelhos da conta Smart Life (pra achar o device_id) */
    @GetMapping("/lampada/dispositivos")
    public Object dispositivosTuya(HttpServletRequest req) {
        Auth.exigir(req);
        if (!tuya.configurado()) throw new ApiException(400, "Configure TUYA_CLIENT_ID e TUYA_SECRET primeiro.");
        return tuya.dispositivosDaConta();
    }

    /** PUT /api/casa/lampada { device_id } -> liga uma lâmpada específica a este usuário */
    @PutMapping("/lampada")
    public Map<String, Object> escolherLampada(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        db.executar("UPDATE dispositivos SET tuya_device_id = ? WHERE usuario_id = ? AND tipo = 'iluminacao'",
                Json.texto(corpo, "device_id", null), uid);
        return ok(null);
    }

    /** POST /api/casa/lampada/testar -> pisca: acende forte e volta pra 30% */
    @PostMapping("/lampada/testar")
    public Map<String, Object> testar(HttpServletRequest req) {
        String deviceId = casa.lampadaDo(Auth.exigir(req).id());
        try {
            tuya.luzBranca(deviceId, 100, 50);
            Thread.sleep(1500);
            tuya.luzBranca(deviceId, 30, 20);
            return Json.obj("ok", true, "modo", tuya.configurado() ? "real" : "simulacao");
        } catch (TuyaService.LampadaException e) {
            throw new ApiException(502, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ok(null);
        }
    }

    /** POST /api/casa/lampada/cor { hex: "#ff8800", brilho: 60 } -> deixa a luz colorida */
    @PostMapping("/lampada/cor")
    public Map<String, Object> cor(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        long uid = Auth.exigir(req).id();
        Long brilho = Json.numero(corpo, "brilho");
        try {
            tuya.cor(casa.lampadaDo(uid), Json.texto(corpo, "hex", "#ffffff"), brilho == null ? 70 : brilho.intValue());
            return ok(null);
        } catch (TuyaService.LampadaException e) {
            throw new ApiException(502, e.getMessage());
        }
    }

    // =================================================================
    //  TV E SOM: Bluetooth do computador (caixinhas e fones)
    // =================================================================

    /** GET /api/casa/som/bluetooth -> saída atual + aparelhos pareados (conectados ou não) */
    @GetMapping("/som/bluetooth")
    public Map<String, Object> bluetoothPareados(HttpServletRequest req) {
        Auth.exigir(req);
        JsonNode saida = som.estado();
        Object celulares;
        try {
            celulares = som.celulares();
        } catch (ApiException e) {
            celulares = List.of();   // sem celulares não é motivo pra quebrar o painel
        }
        return Json.obj("saida", saida != null && saida.path("ok").asBoolean() ? saida : null,
                "aparelhos", bluetooth.listar().path("aparelhos"), "celulares", celulares);
    }

    /** POST /api/casa/som/celular/tocar { id } -> o som do celular passa a sair no computador */
    @PostMapping("/som/celular/tocar")
    public Map<String, Object> celularTocar(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        Auth.exigir(req);
        som.tocarCelular(Json.texto(corpo, "id", ""));
        return ok(null);
    }

    /** POST /api/casa/som/celular/parar { id } */
    @PostMapping("/som/celular/parar")
    public Map<String, Object> celularParar(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        Auth.exigir(req);
        som.pararCelular(Json.texto(corpo, "id", ""));
        return ok(null);
    }

    /** GET /api/casa/som/bluetooth/procurar -> aparelhos de áudio por perto, ainda não pareados (~30 s) */
    @GetMapping("/som/bluetooth/procurar")
    public Object bluetoothProcurar(HttpServletRequest req) {
        Auth.exigir(req);
        return bluetooth.procurar().path("aparelhos");
    }

    /** POST /api/casa/som/bluetooth/parear { id, mac } -> pareia e já passa o som pra ele */
    @PostMapping("/som/bluetooth/parear")
    public Map<String, Object> bluetoothParear(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        Auth.exigir(req);
        bluetooth.parear(Json.texto(corpo, "id", ""), Json.texto(corpo, "mac", ""));
        return ok(null);
    }

    /** POST /api/casa/som/bluetooth/conectar { mac } -> conecta um pareado e vira a saída de som */
    @PostMapping("/som/bluetooth/conectar")
    public Map<String, Object> bluetoothConectar(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        Auth.exigir(req);
        bluetooth.conectar(Json.texto(corpo, "mac", ""));
        return ok(null);
    }

    /** POST /api/casa/som/bluetooth/desconectar { mac } */
    @PostMapping("/som/bluetooth/desconectar")
    public Map<String, Object> bluetoothDesconectar(@RequestBody Map<String, Object> corpo, HttpServletRequest req) {
        Auth.exigir(req);
        bluetooth.desconectar(Json.texto(corpo, "mac", ""));
        return ok(null);
    }
}
