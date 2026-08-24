using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

namespace FlowOps.Agent.Core;

public sealed class DpapiCredentialStore
{
    private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("FlowOps Notification Agent credential v1");
    private readonly AgentPaths _paths;
    public DpapiCredentialStore(AgentPaths paths) => _paths = paths;

    public void Save(string credential)
    {
        Directory.CreateDirectory(_paths.Root);
        File.WriteAllBytes(_paths.CredentialFile, Protect(Encoding.UTF8.GetBytes(credential), Entropy));
    }

    public string? Load()
    {
        if (!File.Exists(_paths.CredentialFile)) return null;
        return Encoding.UTF8.GetString(Unprotect(File.ReadAllBytes(_paths.CredentialFile), Entropy));
    }

    public void Clear() { if (File.Exists(_paths.CredentialFile)) File.Delete(_paths.CredentialFile); }

    private static byte[] Protect(byte[] input, byte[] entropy) => Crypt(input, entropy, false);
    private static byte[] Unprotect(byte[] input, byte[] entropy) => Crypt(input, entropy, true);

    private static byte[] Crypt(byte[] input, byte[] entropy, bool decrypt)
    {
        if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException("Windows DPAPI is required.");
        var inputBlob = Blob.From(input); var entropyBlob = Blob.From(entropy); Blob output = default;
        try
        {
            bool ok = decrypt
                ? CryptUnprotectData(ref inputBlob, IntPtr.Zero, ref entropyBlob, IntPtr.Zero, IntPtr.Zero, 0, ref output)
                : CryptProtectData(ref inputBlob, null, ref entropyBlob, IntPtr.Zero, IntPtr.Zero, 0, ref output);
            if (!ok) throw new Win32Exception(Marshal.GetLastWin32Error());
            var result = new byte[output.Length]; Marshal.Copy(output.Data, result, 0, output.Length); return result;
        }
        finally { inputBlob.Free(); entropyBlob.Free(); if (output.Data != IntPtr.Zero) LocalFree(output.Data); }
    }

    [StructLayout(LayoutKind.Sequential)] private struct Blob
    {
        public int Length; public IntPtr Data;
        public static Blob From(byte[] value) { var b = new Blob { Length = value.Length, Data = Marshal.AllocHGlobal(value.Length) }; Marshal.Copy(value, 0, b.Data, value.Length); return b; }
        public void Free() { if (Data != IntPtr.Zero) Marshal.FreeHGlobal(Data); Data = IntPtr.Zero; }
    }
    [DllImport("crypt32.dll", SetLastError = true, CharSet = CharSet.Unicode)] private static extern bool CryptProtectData(ref Blob input, string? description, ref Blob entropy, IntPtr reserved, IntPtr prompt, int flags, ref Blob output);
    [DllImport("crypt32.dll", SetLastError = true)] private static extern bool CryptUnprotectData(ref Blob input, IntPtr description, ref Blob entropy, IntPtr reserved, IntPtr prompt, int flags, ref Blob output);
    [DllImport("kernel32.dll")] private static extern IntPtr LocalFree(IntPtr memory);
}
