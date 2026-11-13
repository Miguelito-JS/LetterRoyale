package br.com.letterroyale.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lê valores do corpo (body) das requisições, que chega como Map<String,Object>.
 * Ex.: String nome = Json.texto(corpo, "nome", null);
 */
public final class Json {
    private Json() {
    }

    public static String texto(Map<String, Object> m, String chave, String padrao) {
        Object v = m == null ? null : m.get(chave);
        return v == null ? padrao : String.valueOf(v);
    }

    public static Long numero(Map<String, Object> m, String chave) {
        Object v = m == null ? null : m.get(chave);
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double decimal(Map<String, Object> m, String chave) {
        Object v = m == null ? null : m.get(chave);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Boolean bool(Map<String, Object> m, String chave) {
        Object v = m == null ? null : m.get(chave);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    /** Monta um Map rapidinho: Json.obj("ok", true, "id", 5) */
    public static Map<String, Object> obj(Object... chavesEValores) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < chavesEValores.length; i += 2) {
            m.put(String.valueOf(chavesEValores[i]), chavesEValores[i + 1]);
        }
        return m;
    }

    /** Converte 0/1 do MySQL em true/false */
    public static boolean verdade(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return false;
    }
}
