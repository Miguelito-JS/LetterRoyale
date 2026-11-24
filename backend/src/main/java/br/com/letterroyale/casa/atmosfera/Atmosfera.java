package br.com.letterroyale.casa.atmosfera;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * =====================================================================
 *  ATMOSFERA = como a SALA INTEIRA se comporta durante um tipo de filme.
 *
 *  O pôster decide AS CORES da lâmpada (PaletaService).
 *  A atmosfera decide O RESTO:
 *    • brilho      -> quão forte a luz fica (0 a 100)
 *    • ritmo       -> de quanto em quanto tempo a cor troca (calmo | normal | festa)
 *    • efeito      -> como o brilho se mexe (pulsar no terror, relâmpago na ação...)
 *    • cortinas    -> abertas | meio | fechadas
 *    • ar          -> temperatura do ar-condicionado em °C
 *    • volume      -> volume da TV e Som em %
 *
 *  "record" = classe só de dados (Java cria construtor, getters, equals...).
 * =====================================================================
 */
public record Atmosfera(
        String chave,
        String nome,
        String descricao,
        int brilho,
        String ritmo,
        EfeitoLuz.Efeito efeito,
        String cortinas,
        int temperaturaAr,
        int volume) {

    public Atmosfera {
        if (chave == null || chave.isBlank()) throw new IllegalArgumentException("Atmosfera sem chave");
        if (brilho < 5 || brilho > 100) throw new IllegalArgumentException("Brilho fora de 5..100: " + brilho);
        if (volume < 0 || volume > 100) throw new IllegalArgumentException("Volume fora de 0..100: " + volume);
        if (temperaturaAr < 16 || temperaturaAr > 30) throw new IllegalArgumentException("Ar fora de 16..30: " + temperaturaAr);
        if (efeito == null) efeito = EfeitoLuz.Efeito.CONSTANTE;
    }

    /** Pronto pra virar JSON na resposta da API. */
    public Map<String, Object> paraMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chave", chave);
        m.put("nome", nome);
        m.put("descricao", descricao);
        m.put("brilho", brilho);
        m.put("ritmo", ritmo);
        m.put("efeito", efeito.name().toLowerCase());
        m.put("cortinas", cortinas);
        m.put("temperatura_ar", temperaturaAr);
        m.put("volume", volume);
        return m;
    }
}
