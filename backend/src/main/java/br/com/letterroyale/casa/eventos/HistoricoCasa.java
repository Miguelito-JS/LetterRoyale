package br.com.letterroyale.casa.eventos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =====================================================================
 *  Guarda os ÚLTIMOS eventos de cada usuário (em memória).
 *
 *  É uma "fila circular": passou do limite, o mais antigo sai.
 *  Assim a memória nunca cresce sem parar, mesmo com a casa ligada o dia todo.
 *  Classe sem Spring: testada com JUnit puro.
 * =====================================================================
 */
public class HistoricoCasa {
    public static final int LIMITE_PADRAO = 30;

    private final int limite;
    private final Map<Long, Deque<EventoCasa>> porUsuario = new ConcurrentHashMap<>();

    public HistoricoCasa() {
        this(LIMITE_PADRAO);
    }

    public HistoricoCasa(int limite) {
        if (limite < 1) throw new IllegalArgumentException("Limite precisa ser 1 ou mais");
        this.limite = limite;
    }

    public void registrar(EventoCasa e) {
        Deque<EventoCasa> fila = porUsuario.computeIfAbsent(e.usuarioId(), k -> new ArrayDeque<>());
        synchronized (fila) {
            fila.addFirst(e);                              // mais novo na frente
            while (fila.size() > limite) fila.removeLast(); // mais antigo sai
        }
    }

    /** Até "quantos" eventos, do MAIS NOVO para o mais antigo. */
    public List<EventoCasa> ultimos(long usuarioId, int quantos) {
        Deque<EventoCasa> fila = porUsuario.get(usuarioId);
        if (fila == null || quantos <= 0) return List.of();
        synchronized (fila) {
            List<EventoCasa> copia = new ArrayList<>(fila);
            return List.copyOf(copia.subList(0, Math.min(quantos, copia.size())));
        }
    }

    public int limite() {
        return limite;
    }
}
