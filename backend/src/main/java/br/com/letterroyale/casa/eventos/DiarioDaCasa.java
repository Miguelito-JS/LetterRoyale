package br.com.letterroyale.casa.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * =====================================================================
 *  DIÁRIO DA CASA — escuta TODOS os EventoCasa publicados no sistema
 *  e guarda os últimos de cada usuário (linha do tempo da "Sala ao vivo").
 *
 *  @EventListener = o Spring chama este método sozinho sempre que alguém
 *  faz eventos.publishEvent(new EventoCasa(...)). Quem publica nem sabe
 *  que o diário existe -> dá pra criar outros "ouvintes" sem mexer no resto.
 * =====================================================================
 */
@Component
public class DiarioDaCasa {
    private static final Logger log = LoggerFactory.getLogger(DiarioDaCasa.class);
    private final HistoricoCasa historico = new HistoricoCasa();

    @EventListener
    public void aoAcontecer(EventoCasa e) {
        historico.registrar(e);
        log.info("{} [usuário {}] {}", e.tipo().icone(), e.usuarioId(), e.texto());
    }

    public List<Map<String, Object>> ultimos(long usuarioId, int quantos) {
        return historico.ultimos(usuarioId, quantos).stream().map(EventoCasa::paraMapa).toList();
    }
}
