# Bluetooth de áudio do Windows (caixinhas, fones...). Usado pelo BluetoothService.java.
# Roda UMA vez por comando e imprime UMA linha JSON:
#   listar             -> aparelhos de áudio: pareados (conectados ou não) + os que estão por perto
#   parear <id>        -> pareia um aparelho novo (o Windows conecta o som sozinho depois)
#   conectar <mac>     -> conecta um aparelho já pareado e passa o som para ele
#   desconectar <mac>  -> desconecta o som do aparelho (continua pareado)
#   esquecer <id>      -> remove o pareamento
param([string]$comando = 'listar', [string]$id = '')
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

# ---------- WinRT (descobrir e parear) a partir do PowerShell 5.1 ----------
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Devices.Enumeration.DeviceInformation, Windows.Devices.Enumeration, ContentType = WindowsRuntime]
$null = [Windows.Foundation.TypedEventHandler`2, Windows.Foundation, ContentType = WindowsRuntime]
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
function Esperar($op, [Type]$tipo, [int]$segundos = 30) {
    $t = $asTask.MakeGenericMethod($tipo).Invoke($null, @($op))
    if (-not $t.Wait($segundos * 1000)) { throw 'O aparelho demorou demais para responder.' }
    $t.Result
}

# ---------- COM de áudio (achar a saída do aparelho, conectar, virar saída padrão) ----------
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;

namespace LetterRoyaleBt {
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")] class MMDeviceEnumeratorCo {}
    [ComImport, Guid("870AF99C-171D-4F9E-AF0D-E63DF40C2BC9")] class PolicyConfigCo {}

    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceEnumerator {
        int EnumAudioEndpoints(int dataFlow, int stateMask, out IMMDeviceCollection devices);
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice endpoint);
        int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IMMDevice device);
    }
    [Guid("0BD7A1BE-7A1A-44DB-8397-CC5392387B5E"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceCollection { int GetCount(out int n); int Item(int i, out IMMDevice d); }
    [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDevice {
        int Activate(ref Guid iid, int clsCtx, IntPtr p, [MarshalAs(UnmanagedType.IUnknown)] out object o);
        int OpenPropertyStore(int access, out IPropertyStore store);
        int GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
        int GetState(out int state);
    }
    [StructLayout(LayoutKind.Sequential)] struct PropertyKey { public Guid fmtid; public int pid; }
    [StructLayout(LayoutKind.Explicit)] struct PropVariant { [FieldOffset(0)] public short vt; [FieldOffset(8)] public IntPtr ptr; [FieldOffset(16)] public IntPtr extra; }
    [Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IPropertyStore { int GetCount(out int c); int GetAt(int i, out PropertyKey k); int GetValue(ref PropertyKey k, out PropVariant v); }

    [Guid("2A07407E-6497-4A18-9787-32F79BD0D98F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IDeviceTopology {
        int GetConnectorCount(out int n); int GetConnector(int i, out IConnector c);
        int GetSubunitCount(out int n); int GetSubunit(int i, out IntPtr s);
        int GetPartById(int id, out IntPtr p); int GetDeviceId([MarshalAs(UnmanagedType.LPWStr)] out string id);
    }
    [Guid("9C2C4058-23F5-41DE-877A-DF3AF236A09E"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IConnector {
        int GetType(out int t); int GetDataFlow(out int f); int ConnectTo(IntPtr c); int Disconnect();
        int IsConnected(out bool b); int GetConnectedTo(out IConnector c);
        int GetConnectorIdConnectedTo([MarshalAs(UnmanagedType.LPWStr)] out string s);
        int GetDeviceIdConnectedTo([MarshalAs(UnmanagedType.LPWStr)] out string s);
    }
    [Guid("28F54685-06FD-11D2-B27A-00A0C9223196"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IKsControl {
        int KsProperty(ref KsProp prop, int propLen, IntPtr data, int dataLen, out int returned);
    }
    [StructLayout(LayoutKind.Sequential)] struct KsProp { public Guid set; public int id; public int flags; }

    [Guid("F8679F50-850A-41CF-9C72-430F290290C8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IPolicyConfig {
        int GetMixFormat(); int GetDeviceFormat(); int ResetDeviceFormat(); int SetDeviceFormat();
        int GetProcessingPeriod(); int SetProcessingPeriod(); int GetShareMode(); int SetShareMode();
        int GetPropertyValue(); int SetPropertyValue();
        int SetDefaultEndpoint([MarshalAs(UnmanagedType.LPWStr)] string id, int role);
        int SetEndpointVisibility();
    }

    // Responde sozinho às perguntas do pareamento ("Parear?", "o código bate?", "qual o PIN?").
    // Por reflexão, pra não precisar compilar contra os tipos do Windows Runtime.
    public static class Pareamento {
        public static string Pin = "0000";   // PIN padrão da maioria das caixinhas antigas
        public static void Aceitar(object sender, object args) {
            Type t = args.GetType();
            string tipo = t.GetProperty("PairingKind").GetValue(args, null).ToString();
            if (tipo == "ProvidePin") t.GetMethod("Accept", new[] { typeof(string) }).Invoke(args, new object[] { Pin });
            else t.GetMethod("Accept", Type.EmptyTypes).Invoke(args, null);
        }
    }

    public static class Audio {
        static readonly Guid KSPROPSETID_BtAudio = new Guid("7FA06C40-B8F6-4C7E-8556-E8C33A12E54D");

        static string Texto(IMMDevice d, string fmtid, int pid) {
            IPropertyStore s;
            if (d.OpenPropertyStore(0, out s) != 0) return "";
            var k = new PropertyKey { fmtid = new Guid(fmtid), pid = pid };
            PropVariant v;
            if (s.GetValue(ref k, out v) != 0 || v.vt != 31 || v.ptr == IntPtr.Zero) return "";
            return Marshal.PtrToStringUni(v.ptr);
        }

        // Saídas de som Bluetooth que o Windows conhece (mesmo desconectadas): [id, nome do aparelho, estado]
        // estado: 1 = ativa (conectada), 8 = desconectada
        public static List<string[]> SaidasBluetooth() {
            var r = new List<string[]>();
            var e = (IMMDeviceEnumerator) new MMDeviceEnumeratorCo();
            IMMDeviceCollection col;
            Marshal.ThrowExceptionForHR(e.EnumAudioEndpoints(0, 1 | 8, out col));   // saída; ativas + desconectadas
            int n; col.GetCount(out n);
            for (int i = 0; i < n; i++) {
                IMMDevice d; col.Item(i, out d);
                string fisico = Texto(d, "B3F8FA53-0004-438E-9003-51A46E139BFC", 2);
                if (fisico.IndexOf("BTHENUM", StringComparison.OrdinalIgnoreCase) < 0 &&
                    fisico.IndexOf("BTHHFENUM", StringComparison.OrdinalIgnoreCase) < 0) continue;
                string id; d.GetId(out id);
                int st; d.GetState(out st);
                r.Add(new[] { id, Texto(d, "026E516E-B814-414B-83CD-856D6FEF4822", 2), st.ToString(), fisico });   // 026E... = nome do aparelho
            }
            return r;
        }

        // Liga (0) ou desliga (1) o som Bluetooth do aparelho, pelo driver (o mesmo que o botão "Conectar" do Windows)
        public static void Conexao(string endpointId, bool conectar) {
            var e = (IMMDeviceEnumerator) new MMDeviceEnumeratorCo();
            IMMDevice d;
            Marshal.ThrowExceptionForHR(e.GetDevice(endpointId, out d));
            Guid iidTopo = typeof(IDeviceTopology).GUID;
            object o;
            Marshal.ThrowExceptionForHR(d.Activate(ref iidTopo, 23, IntPtr.Zero, out o));
            IConnector c, outro;
            Marshal.ThrowExceptionForHR(((IDeviceTopology) o).GetConnector(0, out c));
            Marshal.ThrowExceptionForHR(c.GetConnectedTo(out outro));
            string filtro;   // o "filtro KS" do driver Bluetooth
            Marshal.ThrowExceptionForHR(outro.GetDeviceIdConnectedTo(out filtro));
            IMMDevice dk;
            Marshal.ThrowExceptionForHR(e.GetDevice(filtro, out dk));
            Guid iidKs = typeof(IKsControl).GUID;
            Marshal.ThrowExceptionForHR(dk.Activate(ref iidKs, 23, IntPtr.Zero, out o));
            var p = new KsProp { set = KSPROPSETID_BtAudio, id = conectar ? 0 : 1, flags = 2 };   // 0 = RECONNECT, 1 = DISCONNECT; 2 = GET
            int ret;
            Marshal.ThrowExceptionForHR(((IKsControl) o).KsProperty(ref p, Marshal.SizeOf(p), IntPtr.Zero, 0, out ret));
        }

        // Passa o som do Windows para essa saída (vale para tudo: filmes, músicas, o slider de volume do site)
        public static void UsarComoSaida(string endpointId) {
            var pc = (IPolicyConfig) new PolicyConfigCo();
            for (int role = 0; role < 3; role++) Marshal.ThrowExceptionForHR(pc.SetDefaultEndpoint(endpointId, role));
        }
    }
}
'@

function Json($o) { $o | ConvertTo-Json -Compress -Depth 4 }
# MAC sem ":" (no id do driver ele vem no fim: ...&0&AABBCCDDEEFF_C00000000)
function Endereco($s) {
    $m = [regex]::Matches(([string]$s).Replace(':', ''), '(?<![0-9A-Fa-f])[0-9A-Fa-f]{12}(?![0-9A-Fa-f])')
    if ($m.Count) { $m[$m.Count - 1].Value.ToUpper() } else { '' }
}

# Saída de som do aparelho (pelo MAC que aparece no id do driver)
# Só as saídas de som "estéreo" (BTHENUM); as "Hands-Free" (BTHHFENUM) são o modo ligação, sem MAC no id
function Saida($mac) {
    if (-not $mac) { return $null }
    foreach ($s in [LetterRoyaleBt.Audio]::SaidasBluetooth()) { if ((Endereco $s[3]) -eq $mac) { return $s } }
    $null
}

try {
    switch ($comando) {
        'listar' {
            # Pareados: lidos das saídas de som do Windows (instantâneo)
            $r = foreach ($s in [LetterRoyaleBt.Audio]::SaidasBluetooth()) {
                $mac = Endereco $s[3]
                if (-not $mac -or $s[3] -notmatch 'BTHENUM') { continue }
                [ordered]@{ nome = $s[1]; mac = $mac; pareado = $true; conectado = ($s[2] -eq '1') }
            }
            Json @{ ok = $true; aparelhos = @($r) }
        }
        'procurar' {
            # Caixinhas, fones e celulares por perto ainda NÃO pareados (o Windows procura por ~30 s)
            $aqs = 'System.Devices.Aep.ProtocolId:="{e0cbf06c-cd8b-4647-bb8a-263b43f0f974}" AND System.Devices.Aep.IsPaired:=System.StructuredQueryType.Boolean#False'
            $props = [Collections.Generic.List[string]]@('System.Devices.Aep.Bluetooth.Cod.Major', 'System.Devices.Aep.DeviceAddress')
            $lista = Esperar ([Windows.Devices.Enumeration.DeviceInformation]::FindAllAsync($aqs, $props, [Windows.Devices.Enumeration.DeviceInformationKind]::AssociationEndpoint)) ([Windows.Devices.Enumeration.DeviceInformationCollection]) 50
            $r = foreach ($d in $lista) {
                $p = @{}; foreach ($kv in $d.Properties) { $p[$kv.Key] = $kv.Value }   # (o [indice] não funciona no PowerShell 5.1)
                $classe = $p['System.Devices.Aep.Bluetooth.Cod.Major']   # 2 = celular, 4 = áudio/vídeo
                if (($classe -ne 2 -and $classe -ne 4) -or -not $d.Name) { continue }
                [ordered]@{ id = $d.Id; nome = $d.Name; mac = (Endereco $p['System.Devices.Aep.DeviceAddress']); pareado = $false; conectado = $false; celular = ($classe -eq 2) }
            }
            Json @{ ok = $true; aparelhos = @($r) }
        }
        'parear' {
            $d = Esperar ([Windows.Devices.Enumeration.DeviceInformation]::CreateFromIdAsync($id)) ([Windows.Devices.Enumeration.DeviceInformation])
            if ($d.Pairing.IsPaired) { Json @{ ok = $true; status = 'AlreadyPaired' }; break }
            # Pareamento "personalizado": nós respondemos às confirmações (o PairAsync simples
            # fica esperando uma janela do Windows que, rodando no servidor, ninguém vê)
            $custom = $d.Pairing.Custom
            $tipoHandler = [Windows.Foundation.TypedEventHandler[Windows.Devices.Enumeration.DeviceInformationCustomPairing, Windows.Devices.Enumeration.DevicePairingRequestedEventArgs]]
            $null = $custom.add_PairingRequested([Delegate]::CreateDelegate($tipoHandler, [LetterRoyaleBt.Pareamento].GetMethod('Aceitar')))
            $tipos = [Windows.Devices.Enumeration.DevicePairingKinds]'ConfirmOnly, DisplayPin, ProvidePin, ConfirmPinMatch'
            $res = Esperar ($custom.PairAsync($tipos, [Windows.Devices.Enumeration.DevicePairingProtectionLevel]::Default)) ([Windows.Devices.Enumeration.DevicePairingResult]) 30
            $st = [string]$res.Status
            $motivo = @{
                AuthenticationTimeout = 'o aparelho não respondeu a tempo. Ele está em modo de pareamento?'
                ConnectionRejected = 'o aparelho recusou. Se for celular, toque em "Parear" na tela dele.'
                AuthenticationFailure = 'o código não bateu. Se for celular, toque em "Parear" na tela dele.'
                RejectedByHandler = 'o aparelho pediu um código diferente de 0000.'
            }[$st]
            Json @{ ok = ($st -eq 'Paired' -or $st -eq 'AlreadyPaired'); status = $st; erro = "Não pareou: $(if ($motivo) { $motivo } else { $st })" }
        }
        'esquecer' {
            $d = Esperar ([Windows.Devices.Enumeration.DeviceInformation]::CreateFromIdAsync($id)) ([Windows.Devices.Enumeration.DeviceInformation])
            $res = Esperar ($d.Pairing.UnpairAsync()) ([Windows.Devices.Enumeration.DeviceUnpairingResult])
            Json @{ ok = ([string]$res.Status -in 'Unpaired', 'AlreadyUnpaired'); status = [string]$res.Status; erro = "O Windows respondeu: $($res.Status)" }
        }
        { $_ -in 'conectar', 'desconectar' } {
            $mac = Endereco $id
            $s = Saida $mac
            if (-not $s) { throw 'Esse aparelho não tem saída de som no Windows. Pareie primeiro.' }
            [LetterRoyaleBt.Audio]::Conexao($s[0], $comando -eq 'conectar')
            if ($comando -eq 'conectar') {
                # espera o som ficar ativo (até ~8 s) e passa o som do Windows para ele
                for ($i = 0; $i -lt 16; $i++) { $s = Saida $mac; if ($s -and $s[2] -eq '1') { break }; Start-Sleep -Milliseconds 500 }
                if (-not ($s -and $s[2] -eq '1')) { throw 'O aparelho não conectou. Ele está ligado e perto do computador?' }
                [LetterRoyaleBt.Audio]::UsarComoSaida($s[0])
            }
            Json @{ ok = $true }
        }
        default { throw "Comando desconhecido: $comando" }
    }
} catch {
    Json @{ ok = $false; erro = $_.Exception.Message }
}
