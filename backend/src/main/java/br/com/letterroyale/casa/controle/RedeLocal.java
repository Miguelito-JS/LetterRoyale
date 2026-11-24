package br.com.letterroyale.casa.controle;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * =====================================================================
 *  Descobre o IP do computador NA REDE DE CASA (ex.: 192.168.0.15),
 *  pra o celular abrir o site pelo QR code. "localhost" só funciona
 *  no próprio PC; o celular precisa do IP dele no Wi-Fi.
 *
 *  Ignora: o próprio PC (127.0.0.1), placas virtuais (VirtualBox, VMware,
 *  Hyper-V/WSL) e VPNs (Radmin usa 26.x.x.x, que nem é IP de rede local).
 * =====================================================================
 */
public final class RedeLocal {

    private static final List<String> VIRTUAIS = List.of("virtual", "vmware", "vethernet", "hyper-v", "wsl", "docker", "radmin", "hamachi", "tap", "tun");

    private RedeLocal() {
    }

    /** IPs do Wi-Fi/cabo do computador, o mais provável primeiro. Lista vazia se não achar. */
    public static List<String> ips() {
        List<String> achados = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String nome = (ni.getName() + " " + ni.getDisplayName()).toLowerCase(Locale.ROOT);
                if (VIRTUAIS.stream().anyMatch(nome::contains)) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) achados.add(a.getHostAddress());
                }
            }
        } catch (Exception ignorado) { /* sem rede: lista vazia */ }
        return ordenar(achados);
    }

    /** Ordem de preferência: 192.168.x.x (Wi-Fi de casa) > 10.x.x.x > 172.16-31.x.x. */
    public static List<String> ordenar(List<String> ips) {
        List<String> r = new ArrayList<>(ips.stream().filter(RedeLocal::ehRedeLocal).distinct().toList());
        r.sort(Comparator.comparingInt(RedeLocal::prioridade));
        return r;
    }

    static boolean ehRedeLocal(String ip) {
        if (ip == null) return false;
        if (ip.startsWith("192.168.") || ip.startsWith("10.")) return true;
        if (ip.startsWith("172.")) {
            try {
                int segundo = Integer.parseInt(ip.split("\\.")[1]);
                return segundo >= 16 && segundo <= 31;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private static int prioridade(String ip) {
        if (ip.startsWith("192.168.")) return 0;
        if (ip.startsWith("10.")) return 1;
        return 2;
    }
}
