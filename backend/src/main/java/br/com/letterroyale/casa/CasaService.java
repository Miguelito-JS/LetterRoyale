package br.com.letterroyale.casa;

import br.com.letterroyale.casa.aparelhos.AparelhosService;
import br.com.letterroyale.casa.atmosfera.Atmosfera;
import br.com.letterroyale.casa.eventos.EventoCasa;
import br.com.letterroyale.erro.ApiException;
import br.com.letterroyale.util.Db;
import br.com.letterroyale.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Regras da Smart Home. Salva o estado no MySQL e manda pra lâmpada (TuyaService).
 * "TV e Som" muda o volume real do Windows (SomService).
 * [+] Cortinas e ar-condicionado: REAIS se tiverem ID cadastrado (AparelhosService / Tuya), senão simulados.
 */
@Service
public class CasaService {
    private static final Logger log = LoggerFactory.getLogger(CasaService.class);
    private final Db db;
    private final TuyaService tuya;
    private final SomService som;
    private final ApplicationEventPublisher eventos;   // avisa a casa toda (DiarioDaCasa e outros ouvintes)
    private final AparelhosService aparelhos;           // cortina e ar de verdade (Tuya)
    private final ScheduledExecutorService agenda = Executors.newSingleThreadScheduledExecutor();

    public CasaService(Db db, TuyaService tuya, SomService som, ApplicationEventPublisher eventos, AparelhosService aparelhos) {
        this.aparelhos = aparelhos;
        this.db = db;
        this.tuya = tuya;
        this.som = som;
        this.eventos = eventos;
    }

    /** Publica um acontecimento da casa (quem escuta decide o que fazer). */
    public void publicar(long uid, EventoCasa.Tipo tipo, String texto) {
        eventos.publishEvent(EventoCasa.agora(uid, tipo, texto));
    }

    /** device_id da lâmpada desse usuário (do banco ou o padrão da configuração) */
    public String lampadaDo(long uid) {
        String doBanco = db.um("SELECT tuya_device_id FROM dispositivos WHERE usuario_id = ? AND tipo = 'iluminacao'", uid)
                .map(d -> (String) d.get("tuya_device_id")).orElse(null);
        return tuya.deviceId(doBanco);
    }

    /**
     * Usuário mexeu num dispositivo (slider / liga-desliga).
     * Devolve null se deu tudo certo, ou um texto de aviso se a lâmpada não respondeu.
     */
    public String aplicarDispositivo(long uid, Map<String, Object> d) {
        publicarMudanca(uid, d);
        if ("tv_som".equals(d.get("tipo"))) return som.aplicar(Json.verdade(d.get("ligado")), ((Number) d.get("valor")).intValue());
        if (!"iluminacao".equals(d.get("tipo"))) return aparelhos.aplicar(d);   // cortina/ar: real se tiver ID cadastrado
        // Modo Cinema ligado e a luz continua acesa? Quem manda a cor é o CinemaService
        // (ele usa o brilho novo do slider na próxima troca) -> não manda luz branca aqui.
        boolean cinema = db.existe("SELECT 1 FROM cinema_ativo WHERE usuario_id = ? AND terminou = FALSE", uid);
        if (cinema && Json.verdade(d.get("ligado"))) return null;
        try {
            tuya.luz(lampadaDo(uid), Json.verdade(d.get("ligado")), ((Number) d.get("valor")).intValue());
            return null;
        } catch (TuyaService.LampadaException e) {
            log.warn(e.getMessage());
            return "Salvo, mas a lâmpada não respondeu: " + e.getMessage();
        }
    }

    /** Ativa uma cena (Modo Cinema, Maratona...): atualiza o banco e manda pra lâmpada. */
    public String aplicarCena(long uid, long cenaId) {
        Map<String, Object> c = db.um("SELECT * FROM cenas WHERE id = ? AND usuario_id = ?", cenaId, uid)
                .orElseThrow(() -> new ApiException(404, "Cena não encontrada."));
        int luz = ((Number) c.get("luz")).intValue();
        // Outra cena -> sai do Modo Cinema (as cores do pôster param)
        db.executar("DELETE FROM cinema_ativo WHERE usuario_id = ? AND terminou = FALSE", uid);

        db.executar("UPDATE dispositivos SET valor = ?, ligado = TRUE WHERE usuario_id = ? AND tipo = 'iluminacao'", luz, uid);
        db.executar("UPDATE dispositivos SET estado = ? WHERE usuario_id = ? AND tipo = 'cortinas'", c.get("cortinas"), uid);
        db.executar("UPDATE dispositivos SET valor = ?, ligado = TRUE WHERE usuario_id = ? AND tipo = 'temperatura'", c.get("temperatura"), uid);
        db.executar("UPDATE dispositivos SET valor = ?, ligado = TRUE WHERE usuario_id = ? AND tipo = 'tv_som'", c.get("volume"), uid);
        db.executar("UPDATE cenas SET ativa = (id = ?) WHERE usuario_id = ?", cenaId, uid);

        String avisoSom = juntarAvisos(som.aplicar(true, ((Number) c.get("volume")).intValue()),
                aparelhos.aplicarTipo(uid, "cortinas"), aparelhos.aplicarTipo(uid, "temperatura"));
        publicar(uid, EventoCasa.Tipo.CENA, "Cena " + c.get("nome") + " ativada: luz " + luz + "%, cortinas "
                + c.get("cortinas") + ", ar " + c.get("temperatura") + "°C, volume " + c.get("volume") + "%");

        String lampada = lampadaDo(uid);
        String cor = (String) c.get("luz_cor");
        int temp = c.get("luz_temperatura") == null ? 20 : ((Number) c.get("luz_temperatura")).intValue();
        try {
            if (Json.verdade(c.get("luz_gradual"))) {
                subirAosPoucos(lampada, luz, temp);            // "Créditos": luz sobe aos poucos
            } else if (cor != null && !cor.isBlank()) {
                tuya.cor(lampada, cor, luz);                   // cena colorida
            } else {
                tuya.luzBranca(lampada, luz, temp);            // cena em luz branca (quente/fria)
            }
            return avisoSom;
        } catch (TuyaService.LampadaException e) {
            log.warn(e.getMessage());
            return "Cena salva, mas a lâmpada não respondeu: " + e.getMessage();
        }
    }

    /**
     * ATMOSFERA do filme (terror, ação...): a sala inteira se ajusta de uma vez.
     * A luz NÃO é mandada aqui: quem cuida das cores é o CinemaService (brilho = atmosfera.brilho()).
     * Devolve um aviso se o som real não respondeu (ou null).
     */
    public String aplicarAmbiente(long uid, Atmosfera a) {
        db.executar("UPDATE dispositivos SET valor = ?, ligado = TRUE WHERE usuario_id = ? AND tipo = 'iluminacao'", a.brilho(), uid);
        db.executar("UPDATE dispositivos SET estado = ? WHERE usuario_id = ? AND tipo = 'cortinas'", a.cortinas(), uid);
        db.executar("UPDATE dispositivos SET valor = ?, ligado = TRUE WHERE usuario_id = ? AND tipo = 'temperatura'", a.temperaturaAr(), uid);
        db.executar("UPDATE dispositivos SET valor = ?, ligado = TRUE WHERE usuario_id = ? AND tipo = 'tv_som'", a.volume(), uid);
        String aviso = juntarAvisos(som.aplicar(true, a.volume()),
                aparelhos.aplicarTipo(uid, "cortinas"), aparelhos.aplicarTipo(uid, "temperatura"));
        publicar(uid, EventoCasa.Tipo.ATMOSFERA, "Atmosfera " + a.nome() + ": " + a.descricao());
        publicar(uid, EventoCasa.Tipo.CORTINAS, "Cortinas " + a.cortinas());
        publicar(uid, EventoCasa.Tipo.AR, "Ar-condicionado em " + a.temperaturaAr() + "°C");
        publicar(uid, EventoCasa.Tipo.SOM, "Volume em " + a.volume() + "%");
        return aviso;
    }

    /** Vários avisos ("som não respondeu", "cortina não respondeu") viram um texto só (ou null). */
    static String juntarAvisos(String... avisos) {
        String r = java.util.Arrays.stream(avisos).filter(a -> a != null && !a.isBlank())
                .collect(java.util.stream.Collectors.joining(" • "));
        return r.isEmpty() ? null : r;
    }

    /** Usuário mexeu num dispositivo na mão -> vira evento na linha do tempo. */
    private void publicarMudanca(long uid, Map<String, Object> d) {
        String tipo = String.valueOf(d.get("tipo"));
        boolean ligado = Json.verdade(d.get("ligado"));
        Object valor = d.get("valor");
        switch (tipo) {
            case "iluminacao" -> publicar(uid, EventoCasa.Tipo.LUZ, ligado ? "Luz em " + valor + "%" : "Luz desligada");
            case "cortinas" -> publicar(uid, EventoCasa.Tipo.CORTINAS, "Cortinas " + d.get("estado"));
            case "temperatura" -> publicar(uid, EventoCasa.Tipo.AR, ligado ? "Ar-condicionado em " + valor + "°C" : "Ar-condicionado desligado");
            case "tv_som" -> publicar(uid, EventoCasa.Tipo.SOM, ligado ? "Volume em " + valor + "%" : "Som no mudo");
            default -> { /* tipo novo: sem evento */ }
        }
    }

    /** Sobe o brilho em 5 passos durante ~10 segundos (efeito de fim de filme). */
    private void subirAosPoucos(String lampada, int brilhoFinal, int temp) {
        int passos = 5;
        for (int i = 1; i <= passos; i++) {
            int brilho = Math.max(5, brilhoFinal * i / passos);
            agenda.schedule(() -> {
                try {
                    tuya.luzBranca(lampada, brilho, temp);
                } catch (Exception e) {
                    log.warn("Falha no passo do efeito gradual: {}", e.getMessage());
                }
            }, (long) (i - 1) * 2, TimeUnit.SECONDS);
        }
    }

    /**
     * "Próxima sessão" com "Modo Cinema automático":
     * a cada 1 minuto, procura sessões cujo horário chegou e ativa a cena escolhida.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void rodarSessoesAgendadas() {
        List<Map<String, Object>> prontas = db.lista("""
                SELECT id, usuario_id, cena_id FROM sessoes_agendadas
                 WHERE executada = FALSE AND cena_id IS NOT NULL AND data_hora <= NOW()
                """);
        for (Map<String, Object> s : prontas) {
            long uid = ((Number) s.get("usuario_id")).longValue();
            long cena = ((Number) s.get("cena_id")).longValue();
            db.executar("UPDATE sessoes_agendadas SET executada = TRUE WHERE id = ?", s.get("id"));
            log.info("🎬 Sessão {} começou: ativando cena {} do usuário {}", s.get("id"), cena, uid);
            try {
                aplicarCena(uid, cena);
            } catch (Exception e) {
                log.warn("Não consegui ativar a cena agendada: {}", e.getMessage());
            }
        }
    }
}
