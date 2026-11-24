package br.com.letterroyale.casa.controle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =====================================================================
 *  SESSÃO COMPARTILHADA: os amigos escaneiam o QR code e votam
 *  no filme da noite pelo celular (sem precisar de conta).
 *
 *  • cada celular vota 1 vez (pode trocar o voto; vale o último)
 *  • empate -> ganha o filme que o anfitrião colocou primeiro
 *  Classe sem Spring e sem banco: testada com JUnit puro.
 * =====================================================================
 */
public class Votacao {
    /** Um filme candidato (só o que a tela precisa). */
    /** Sem limite pequeno: a turma põe quantos filmes quiser (40 é só pra ninguém travar o site). */
    public static final int MAXIMO = 40;

    public record Opcao(long tmdbId, String titulo, String poster) {
    }

    private final String codigo;
    private final long anfitriaoId;
    private final String anfitriao;
    private final List<Opcao> opcoes;
    private final long criadaEm;
    private final Map<String, Long> votos = new ConcurrentHashMap<>();   // celular -> filme
    private volatile boolean aberta = true;

    public Votacao(String codigo, long anfitriaoId, String anfitriao, List<Opcao> opcoes, long criadaEm) {
        if (opcoes == null || opcoes.size() < 2) throw new IllegalArgumentException("Escolha pelo menos 2 filmes pra votação.");
        if (opcoes.size() > MAXIMO) throw new IllegalArgumentException("No máximo " + MAXIMO + " filmes na votação.");   // só uma trava de segurança
        if (opcoes.stream().map(Opcao::tmdbId).distinct().count() != opcoes.size()) throw new IllegalArgumentException("Filme repetido na votação.");
        this.codigo = codigo;
        this.anfitriaoId = anfitriaoId;
        this.anfitriao = anfitriao;
        this.opcoes = List.copyOf(opcoes);
        this.criadaEm = criadaEm;
    }

    /** Registra (ou troca) o voto deste celular. */
    public void votar(String votante, long tmdbId) {
        if (!aberta) throw new IllegalStateException("A votação já terminou.");
        if (votante == null || votante.isBlank() || votante.length() > 64) throw new IllegalArgumentException("Celular sem identificação.");
        if (opcoes.stream().noneMatch(o -> o.tmdbId() == tmdbId)) throw new IllegalArgumentException("Esse filme não está na votação.");
        votos.put(votante, tmdbId);
    }

    /** Votos por filme, na ordem das opções. */
    public Map<Long, Integer> contagem() {
        Map<Long, Integer> c = new LinkedHashMap<>();
        opcoes.forEach(o -> c.put(o.tmdbId(), 0));
        votos.values().forEach(id -> c.merge(id, 1, Integer::sum));
        return c;
    }

    /** Filme vencedor (empate = o primeiro da lista). */
    public Opcao vencedor() {
        Map<Long, Integer> c = contagem();
        Opcao melhor = opcoes.get(0);
        for (Opcao o : opcoes) if (c.get(o.tmdbId()) > c.get(melhor.tmdbId())) melhor = o;
        return melhor;
    }

    public Opcao encerrar() {
        aberta = false;
        return vencedor();
    }

    /** O que o celular do votante vê (seuVoto = em quem ESTE celular votou). */
    public Map<String, Object> paraMapa(String votante) {
        Map<Long, Integer> c = contagem();
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Opcao o : opcoes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.tmdbId());
            m.put("titulo", o.titulo());
            m.put("poster", o.poster());
            m.put("votos", c.get(o.tmdbId()));
            lista.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("codigo", codigo);
        r.put("anfitriao", anfitriao);
        r.put("aberta", aberta);
        r.put("total_votos", votos.size());
        r.put("opcoes", lista);
        r.put("vencedor", aberta ? null : vencedor().tmdbId());
        r.put("seu_voto", votante == null ? null : votos.get(votante));
        return r;
    }

    public String codigo() { return codigo; }
    public long anfitriaoId() { return anfitriaoId; }
    public long criadaEm() { return criadaEm; }
    public boolean aberta() { return aberta; }
    public List<Opcao> opcoes() { return opcoes; }
}
