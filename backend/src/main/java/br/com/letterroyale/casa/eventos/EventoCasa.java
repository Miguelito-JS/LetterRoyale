package br.com.letterroyale.casa.eventos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * =====================================================================
 *  Um ACONTECIMENTO na casa: "sessão começou", "cortinas fecharam",
 *  "créditos subiram"...
 *
 *  Quem faz a ação (CinemaService, CasaService) só PUBLICA o evento.
 *  Quem quer saber (DiarioDaCasa, e no futuro: notificação no celular,
 *  estatísticas, outros aparelhos) ESCUTA. Um não conhece o outro.
 *  Isso é arquitetura ORIENTADA A EVENTOS (padrão Observer).
 * =====================================================================
 */
public record EventoCasa(long usuarioId, Tipo tipo, String texto, long instanteMs) {

    /** O que aconteceu. O ícone é o que a tela mostra na linha do tempo. */
    public enum Tipo {
        SESSAO_INICIADA("🎬"),
        ATMOSFERA("🌌"),
        CREDITOS("🎞️"),
        CINEMA_DESLIGADO("⏹️"),
        CENA("✨"),
        LUZ("💡"),
        CORTINAS("🪟"),
        AR("❄️"),
        SOM("🔊"),
        INTERVALO("⏸️"),
        RETOMADA("▶️"),
        VOTACAO("🗳️"),
        CONTROLE("📱");

        private final String icone;

        Tipo(String icone) {
            this.icone = icone;
        }

        public String icone() {
            return icone;
        }
    }

    public EventoCasa {
        if (tipo == null) throw new IllegalArgumentException("Evento sem tipo");
        if (texto == null) texto = "";
    }

    /** Atalho: evento acontecendo agora. */
    public static EventoCasa agora(long usuarioId, Tipo tipo, String texto) {
        return new EventoCasa(usuarioId, tipo, texto, System.currentTimeMillis());
    }

    /** Pronto pra virar JSON na resposta da API. */
    public Map<String, Object> paraMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo.name().toLowerCase());
        m.put("icone", tipo.icone());
        m.put("texto", texto);
        m.put("instante", instanteMs);
        return m;
    }
}
