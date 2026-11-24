package com.rotarypumpers.meraki.core;

import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SecureRecorderChecks {
    private static int count;

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        count++;
    }

    private static void reject(SecureArmStream.Decoder decoder, JSONObject row, String reason) throws Exception {
        try {
            decoder.decode(row.toString());
        } catch (IOException expected) {
            check(expected.getMessage().contains(reason), reason);
            return;
        }
        throw new AssertionError(reason);
    }

    private static JSONObject metadata() throws Exception {
        return new JSONObject()
                .put("type", "metadata")
                .put("format", "rp-display-cache-v1")
                .put("stream_protocol", 2)
                .put("display_sha256", "df0c10a7c72a710ff5fb703c39e97a47e69f4fc2032e0fb6be54000784665473")
                .put("pid", 1).put("start_ticks", 0).put("app_pointer", 65536)
                .put("register_count", 112).put("period_ms", 500)
                .put("source", "display_cache").put("sensor_synchronous", false);
    }

    private static JSONObject sample(long seq, long uptime, long gap) throws Exception {
        return new JSONObject().put("type", "sample").put("seq", seq)
                .put("uptime_ns", uptime).put("gap_ns", gap).put("read_duration_ns", 1000)
                .put("registers", new JSONArray(new int[112]))
                .put("preferences", new JSONArray(new int[13]));
    }

    public static void main(String[] args) throws Exception {
        String privateFixture = String.join(".", "192", "168", "001", "013");
        check(MachineAddress.privateIpv4(privateFixture).equals(String.join(".", "192", "168", "1", "13")),
                "normalizes private IPv4 literals");
        try {
            MachineAddress.privateIpv4(String.join(".", "203", "0", "113", "8"));
            throw new AssertionError("rejects non-private IPv4");
        } catch (IllegalArgumentException expected) {
            count++;
        }
        String pairingJson = new JSONObject().put("protocol", "rp-secure-stream-v1").put("port", 9443)
                .put("certificate_sha256", "a".repeat(64)).put("token", "b".repeat(64)).toString();
        PairingQrPayload pairing = PairingQrPayload.parse(pairingJson);
        check(pairing.host == null, "hostless pairing stays hostless until confirmation");
        check(pairing.withConfirmedAddress(privateFixture).host.equals(MachineAddress.privateIpv4(privateFixture)),
                "hostless pairing uses the confirmed address");
        SecureArmStream.Decoder decoder = new SecureArmStream.Decoder();
        reject(decoder, sample(0, 1_000_000_000, 0), "sequence mismatch");
        reject(decoder, metadata().put("stream_protocol", 3), "identity or format mismatch");
        check(decoder.decode(metadata().toString()).getString("type").equals("metadata"), "accepts paired stream metadata");
        check(decoder.sample(decoder.decode(sample(0, 1_000_000_000, 0).toString())).uptime == 1.0,
                "decodes the first sample in seconds");
        check(decoder.sample(decoder.decode(sample(1, 1_500_000_000, 500_000_000).toString())).uptime == 1.5,
                "accepts the next sequential sample");
        reject(decoder, sample(3, 2_000_000_000, 500_000_000), "sequence mismatch");
        reject(new SecureArmStream.Decoder(), metadata().put("sensor_synchronous", true), "identity or format mismatch");
        System.out.println("SECURE_RECORDER_CHECKS_OK checks=" + count);
    }
}
