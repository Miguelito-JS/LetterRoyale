# Controla o volume da SAÍDA DE SOM PADRÃO do Windows (caixinha/fone Bluetooth, alto-falante...).
# Usado pelo SomService.java: fica aberto e recebe um comando por linha, responde uma linha JSON.
#   get        -> {"ok":true,"nome":"JBL Flip 5","volume":30,"mudo":false,"bluetooth":true}
#   set 45     -> muda o volume para 45% (e tira do mudo)
#   mute 1|0   -> muda / tira do mudo
#   celulares / tocar <id> / parar <id> -> celular tocando no computador (ver mais abaixo)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8   # nomes com acento chegam certos no Java
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

namespace LetterRoyale {
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")] class MMDeviceEnumeratorCo {}

    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceEnumerator {
        int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice endpoint);
    }

    [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDevice {
        int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object iface);
        int OpenPropertyStore(int access, out IPropertyStore store);
        int GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
    }

    [StructLayout(LayoutKind.Sequential)] struct PropertyKey { public Guid fmtid; public int pid; }

    [StructLayout(LayoutKind.Explicit)] struct PropVariant {
        [FieldOffset(0)] public short vt;
        [FieldOffset(8)] public IntPtr ptr;
        [FieldOffset(16)] public IntPtr extra;
    }

    [Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IPropertyStore {
        int GetCount(out int count);
        int GetAt(int i, out PropertyKey key);
        int GetValue(ref PropertyKey key, out PropVariant value);
    }

    [Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioEndpointVolume {
        int RegisterControlChangeNotify(IntPtr n);
        int UnregisterControlChangeNotify(IntPtr n);
        int GetChannelCount(out int c);
        int SetMasterVolumeLevel(float db, ref Guid ctx);
        int SetMasterVolumeLevelScalar(float level, ref Guid ctx);
        int GetMasterVolumeLevel(out float db);
        int GetMasterVolumeLevelScalar(out float level);
        int SetChannelVolumeLevel(int ch, float db, ref Guid ctx);
        int SetChannelVolumeLevelScalar(int ch, float level, ref Guid ctx);
        int GetChannelVolumeLevel(int ch, out float db);
        int GetChannelVolumeLevelScalar(int ch, out float level);
        int SetMute([MarshalAs(UnmanagedType.Bool)] bool mute, ref Guid ctx);
        int GetMute([MarshalAs(UnmanagedType.Bool)] out bool mute);
    }

    public static class Som {
        static IMMDevice Padrao() {
            var e = (IMMDeviceEnumerator) new MMDeviceEnumeratorCo();
            IMMDevice d;
            Marshal.ThrowExceptionForHR(e.GetDefaultAudioEndpoint(0, 1, out d));   // 0 = saída, 1 = multimídia
            return d;
        }

        static IAudioEndpointVolume Volume() {
            Guid iid = typeof(IAudioEndpointVolume).GUID;
            object o;
            Marshal.ThrowExceptionForHR(Padrao().Activate(ref iid, 23, IntPtr.Zero, out o));
            return (IAudioEndpointVolume) o;
        }

        static string Texto(IPropertyStore s, string fmtid, int pid) {
            var k = new PropertyKey { fmtid = new Guid(fmtid), pid = pid };
            PropVariant v;
            if (s.GetValue(ref k, out v) != 0 || v.vt != 31 || v.ptr == IntPtr.Zero) return "";   // 31 = texto
            return Marshal.PtrToStringUni(v.ptr);
        }

        // Nome que aparece no Windows (ex.: "Fones de ouvido (JBL Tune 510BT)") e o "pai" do aparelho
        public static string[] Info() {
            IPropertyStore s;
            Marshal.ThrowExceptionForHR(Padrao().OpenPropertyStore(0, out s));
            return new[] {
                Texto(s, "A45C254E-DF1C-4EFD-8020-67D146A850E0", 14),   // nome do aparelho
                Texto(s, "A45C254E-DF1C-4EFD-8020-67D146A850E0", 2),    // descrição (Alto-falantes, Fones...)
                Texto(s, "B3F8FA53-0004-438E-9003-51A46E139BFC", 2),    // id do aparelho físico (BTHENUM = Bluetooth)
                Texto(s, "A45C254E-DF1C-4EFD-8020-67D146A850E0", 24),   // enumerador
            };
        }

        public static int Get() { float f; Volume().GetMasterVolumeLevelScalar(out f); return (int) Math.Round(f * 100); }
        public static bool Mudo() { bool m; Volume().GetMute(out m); return m; }
        public static void Set(int pct) { Guid g = Guid.Empty; var v = Volume(); v.SetMasterVolumeLevelScalar(Math.Max(0, Math.Min(100, pct)) / 100f, ref g); v.SetMute(false, ref g); }
        public static void Mute(bool m) { Guid g = Guid.Empty; Volume().SetMute(m, ref g); }
    }
}
'@

function Json($o) { $o | ConvertTo-Json -Compress -Depth 4 }

# ---------- Celular tocando NO computador (o PC vira a "caixinha Bluetooth" do celular) ----------
#   celulares      -> celulares pareados que podem mandar som pra cá
#   tocar <id>     -> abre a conexão (o som do celular sai no PC)
#   parar <id>     -> fecha
# A conexão só vive enquanto este processo estiver aberto -> por isso fica aqui, e não no bluetooth.ps1.
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Audio.AudioPlaybackConnection, Windows.Media.Audio, ContentType = WindowsRuntime]
$null = [Windows.Devices.Enumeration.DeviceInformation, Windows.Devices.Enumeration, ContentType = WindowsRuntime]
$metodos = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 }
$asTaskOp = $metodos | Where-Object { $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
$asTaskAcao = $metodos | Where-Object { $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction' } | Select-Object -First 1
function Esperar($op, [Type]$tipo) {
    $t = if ($tipo) { $asTaskOp.MakeGenericMethod($tipo).Invoke($null, @($op)) } else { $asTaskAcao.Invoke($null, @($op)) }
    if (-not $t.Wait(15000)) { throw 'O celular demorou demais para responder.' }
    if ($tipo) { $t.Result }
}
$conexoes = @{}   # id -> AudioPlaybackConnection aberta
$nomes = @{}      # id -> nome do celular

function Celulares {
    $sel = [Windows.Media.Audio.AudioPlaybackConnection]::GetDeviceSelector()
    $lista = Esperar ([Windows.Devices.Enumeration.DeviceInformation]::FindAllAsync($sel)) ([Windows.Devices.Enumeration.DeviceInformationCollection])
    @(foreach ($d in $lista) {
        $c = $conexoes[$d.Id]
        [ordered]@{ id = $d.Id; nome = $d.Name; tocando = ($null -ne $c -and [string]$c.State -eq 'Opened') }
    })
}

function Tocar($id) {
    if ($conexoes[$id]) { $conexoes[$id].Dispose(); $conexoes.Remove($id) }
    $c = [Windows.Media.Audio.AudioPlaybackConnection]::TryCreateFromId($id)
    if (-not $c) { throw 'Esse celular não aceita mandar som para o computador.' }
    Esperar ($c.StartAsync()) $null
    $r = Esperar ($c.OpenAsync()) ([Windows.Media.Audio.AudioPlaybackConnectionOpenResult])
    if ([string]$r.Status -ne 'Success') { $c.Dispose(); throw "O celular não conectou ($($r.Status)). Ele está com o Bluetooth ligado e perto?" }
    $conexoes[$id] = $c
    try { $nomes[$id] = (Esperar ([Windows.Devices.Enumeration.DeviceInformation]::CreateFromIdAsync($id)) ([Windows.Devices.Enumeration.DeviceInformation])).Name } catch { $nomes[$id] = 'Celular' }
}

# Nomes dos celulares com a conexão aberta agora (vai junto no "get": o card mostra "📱 Galaxy → ...")
function Tocando {
    @(foreach ($id in @($conexoes.Keys)) {
        if ([string]$conexoes[$id].State -eq 'Opened') { $nomes[$id] } else { $conexoes[$id].Dispose(); $conexoes.Remove($id) }   # celular saiu de perto
    })
}

while ($null -ne ($linha = [Console]::In.ReadLine())) {
    try {
        $p = $linha.Trim().Split(' ', 2)
        $resposta = $null   # comandos de celular respondem com outra coisa (não o estado do volume)
        switch ($p[0]) {
            'set'  { [LetterRoyale.Som]::Set([int]$p[1]) }
            'mute' { [LetterRoyale.Som]::Mute($p[1] -eq '1') }
            'celulares' { $resposta = @{ ok = $true; celulares = [object[]]@(Celulares) } }
            'tocar' { Tocar $p[1]; $resposta = @{ ok = $true } }
            'parar' { if ($conexoes[$p[1]]) { $conexoes[$p[1]].Dispose(); $conexoes.Remove($p[1]) }; $resposta = @{ ok = $true } }
        }
        if ($resposta) { Json $resposta; continue }
        $i = [LetterRoyale.Som]::Info()
        $bt = ($i[2] + ' ' + $i[3]) -match 'BTH|Bluetooth'
        Json @{ ok = $true; nome = $i[0]; tipo = $i[1]; volume = [LetterRoyale.Som]::Get(); mudo = [LetterRoyale.Som]::Mudo(); bluetooth = $bt; celulares_tocando = [object[]]@(Tocando) }
    } catch {
        Json @{ ok = $false; erro = $_.Exception.Message }
    }
}
