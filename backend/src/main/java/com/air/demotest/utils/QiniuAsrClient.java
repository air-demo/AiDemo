package com.air.demotest.utils;

import com.air.demotest.config.QiniuAsrProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okio.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

public class QiniuAsrClient {

    // —— 协议常量（与你 Python 示例一致）——
    private static final int PROTOCOL_VERSION      = 0b0001;
    // Message Types
    private static final int FULL_CLIENT_REQUEST   = 0b0001;
    private static final int AUDIO_ONLY_REQUEST    = 0b0010;
    private static final int FULL_SERVER_RESPONSE  = 0b1001;
    private static final int SERVER_ACK            = 0b1011;
    private static final int SERVER_ERROR_RESPONSE = 0b1111;
    // Message Type Specific Flags
    private static final int NO_SEQUENCE           = 0b0000;
    private static final int POS_SEQUENCE          = 0b0001; // 有序列号
    private static final int NEG_SEQUENCE          = 0b0010; // is_last_package
    private static final int NEG_WITH_SEQUENCE     = 0b0011; // 有序列号 + is_last_package
    // 序列化和压缩方式
    private static final int NO_SERIALIZATION      = 0b0000;
    private static final int JSON_SERIALIZATION    = 0b0001;
    private static final int NO_COMPRESSION        = 0b0000;
    private static final int GZIP_COMPRESSION      = 0b0001;

    private final OkHttpClient httpClient;
    private final ObjectMapper om = new ObjectMapper();

    private final String token;
    private final String wsUrl;
    private final int segDurationMs;
    private final int sampleRate;
    private final int channels;
    private final int bits;
    private final String codec;
    private final String modelName;
    private final boolean enablePunc;
    private final int overallTimeoutMs;
    private volatile String latestText = "";
    private volatile boolean senderStarted = false;
    CountDownLatch configAcked = new CountDownLatch(1);
    AtomicLong lastRecvAt = new AtomicLong(System.currentTimeMillis());
    ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
    // “静默完成”守卫：若发送完最后一包后 N 秒没新消息，就用当前累积文本完成（兜底）
    final int QUIET_AFTER_LAST_SEND_MS = 5000;
    final AtomicBoolean lastSent = new AtomicBoolean(false);

    public QiniuAsrClient(QiniuAsrProps props) {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(props.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket 建议 0（不走 IO 读超时）
                .pingInterval(15, TimeUnit.SECONDS)    // 定期心跳，防中间设备断链
                .build();
        this.token = props.getToken();
        this.wsUrl = props.getWsUrl();
        this.segDurationMs = props.getSegDurationMs();
        this.sampleRate = props.getSampleRate();
        this.channels = props.getChannels();
        this.bits = props.getBits();
        this.codec = props.getCodec();
        this.modelName = props.getModelName();
        this.enablePunc = props.isEnablePunc();
        this.overallTimeoutMs = props.getOverallTimeoutMs();
    }

    /** 主入口：把 PCM 字节流（WAV 去头后的裸 PCM LE16）推送到七牛，返回最终文本 */
    public String recognizePcm(byte[] pcmLE16) throws Exception {

        Request request = new Request.Builder()
                .url(wsUrl)
                .addHeader("Authorization", "Bearer " + token)
                .build();


        CompletableFuture<String> finalTextFuture = new CompletableFuture<>();
        CountDownLatch opened = new CountDownLatch(1);



        WebSocket ws = httpClient.newWebSocket(request, new WebSocketListener() {
            private volatile int seq = 1;
            private final StringBuilder textBuf = new StringBuilder();

            @Override public void onOpen(WebSocket webSocket, Response response) {
                try {
                    // 发送 FULL_CLIENT_REQUEST（带配置 JSON，gzip）
                    byte[] payload = om.writeValueAsBytes(buildInitRequest());
                    byte[] payloadGz = gzip(payload);
                    byte[] frame = buildFrame(
                            FULL_CLIENT_REQUEST,
                            POS_SEQUENCE, // 有序列号
                            JSON_SERIALIZATION,
                            GZIP_COMPRESSION,
                            seq /*sequence*/,
                            payloadGz
                    );
                    webSocket.send(ByteString.of(frame));
                    opened.countDown();

                    // **启动发送音频分段**
                    new Thread(() -> {
                        try {
                            int bytesPerFrame = channels * (bits / 8);
                            int framesNeeded = sampleRate * segDurationMs / 1000;
                            int bytesPerChunk = framesNeeded * bytesPerFrame; // 16k * 0.3s * 2B = 9600
                            int offset = 0;
                            while (offset < pcmLE16.length) {
                                int len = Math.min(bytesPerChunk, pcmLE16.length - offset);
                                byte[] slice = new byte[len];
                                System.arraycopy(pcmLE16, offset, slice, 0, len);
                                offset += len;

                                seq++;
                                byte[] gz = gzip(slice);
                                byte[] audioFrame = buildFrame(
                                        AUDIO_ONLY_REQUEST,
                                        POS_SEQUENCE, // 有序列号（且非最后一包）
                                        JSON_SERIALIZATION,
                                        GZIP_COMPRESSION,
                                        seq,
                                        gz
                                );
                                webSocket.send(ByteString.of(audioFrame));

                                try { Thread.sleep(segDurationMs); } catch (InterruptedException ignored) {}
                            }

                            // **发送结尾空包，标记最后一包（NEG_WITH_SEQUENCE）**
                            seq++;
                            byte[] gzEmpty = gzip(new byte[0]);
                            byte[] lastFrame = buildFrame(
                                    AUDIO_ONLY_REQUEST,
                                    NEG_WITH_SEQUENCE, // is_last_package = true
                                    JSON_SERIALIZATION,
                                    GZIP_COMPRESSION,
                                    seq,
                                    gzEmpty
                            );
                            webSocket.send(ByteString.of(lastFrame));

                            lastSent.set(true);


                            AsrDbg.log("last frame sent. seq=%d", seq);
                        } catch (Exception e) {
                            finalTextFuture.completeExceptionally(e);
                            webSocket.close(1011, "client exception");
                        }
                    }).start();

                } catch (Exception e) {
                    finalTextFuture.completeExceptionally(e);
                    webSocket.close(1011, "init exception");
                }
            }

            @Override public void onMessage(WebSocket webSocket, ByteString bytes) {

                System.out.println("[ASR] recv bytes: " + bytes.size());
                if (!senderStarted) {
                    senderStarted = true;
                    configAcked.countDown();
                    System.out.println("[ASR] first response (config ACK) received.");
                }
                try {
                    ParseResult pr = parseResponse(bytes.toByteArray());
                    // 这里的 pr.payloadMsg 可能是 JSON Map 或字符串，做容错取文本
                    String piece = extractText(pr.payloadMsg);
                    if (piece != null && !piece.isEmpty()) {
                        // 只保存“更长或确定”的结果，防止重复/回退
                        if (piece.length() > latestText.length()) {
                            latestText = piece;
                        }
                        AsrDbg.log("latestText=%s", latestText);
                    }
                    if (pr.isLastPackage || payloadIsFinal(pr.payloadMsg)) {
                        finalTextFuture.complete(latestText.trim());
                        webSocket.close(1000, "done(final)");
                        return;
                    }
                } catch (Exception e) {
                    finalTextFuture.completeExceptionally(e);
                    webSocket.close(1011, "parse error");
                }
            }

            @Override public void onMessage(WebSocket webSocket, String text) {

                System.out.println("[ASR] recv text: " + (text == null ? 0 : text.length()));
                if (!senderStarted) {
                    senderStarted = true;
                    configAcked.countDown();
                    System.out.println("[ASR] first response (config ACK) received (string).");
                }
                // 服务端也可能直接发字符串
                if (text != null && !text.isEmpty()) {
                    finalTextFuture.complete(text);
                    webSocket.close(1000, "done(str)");
                }
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (!finalTextFuture.isDone()) {
                    finalTextFuture.completeExceptionally(
                            new RuntimeException("WebSocket failure", t));
                }
            }

            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(code, reason);
            }

            private Map<String, Object> buildInitRequest() {
                // 与 Python 示例 construct_request 一致
                return java.util.Collections.unmodifiableMap(new java.util.HashMap<String, Object>() {{
                    put("user", java.util.Collections.singletonMap("uid", "java-client"));
                    put("audio", new java.util.HashMap<String, Object>() {{
                        put("format", "pcm");
                        put("sample_rate", sampleRate);
                        put("bits", bits);
                        put("channel", channels);
                        put("codec", codec);
                    }});
                    put("request", new java.util.HashMap<String, Object>() {{
                        put("model_name", modelName);
                        put("enable_punc", enablePunc);
                    }});
                }});
            }

            @SuppressWarnings("unchecked")
            private String extractText(Object payloadMsg) {
                if (!(payloadMsg instanceof Map)) {
                    return (payloadMsg == null) ? null : String.valueOf(payloadMsg);
                }
                Map<String, Object> root = (Map<String, Object>) payloadMsg;

                // 1) 七牛流式会带 prefetch，prefetch=true 的中间态可跳过
                Object prefetch = root.get("prefetch");
                if (prefetch instanceof Boolean && (Boolean) prefetch) return null;

                // 2) 优先取 result.text
                Map<String, Object> result = (Map<String, Object>) root.get("result");
                if (result != null) {
                    Object t = result.get("text");
                    String text = t == null ? null : String.valueOf(t);

                    // 3) 如果有 utterances，挑 definite=true 的那条（最终稳定）
                    Object uts = result.get("utterances");
                    if (uts instanceof Iterable) {
                        String best = null;
                        for (Object uObj : (Iterable<?>) uts) {
                            if (!(uObj instanceof Map)) continue;
                            Map<String, Object> u = (Map<String, Object>) uObj;
                            Object def = u.get("definite");
                            if (def instanceof Boolean && (Boolean) def) {
                                Object ut = u.get("text");
                                if (ut != null) best = String.valueOf(ut);
                            }
                        }
                        if (best != null && !best.isEmpty()) return best;
                    }
                    return (text == null || text.isEmpty()) ? null : text;
                }

                // 4) 兜底字段（有些实现直接在根上给 text/result/transcript）
                Object v = firstNonNull(root.get("text"), root.get("result"), root.get("transcript"), root.get("payload"));
                return v == null ? null : String.valueOf(v);
            }


        });
        boolean ackOk = configAcked.await(5, TimeUnit.SECONDS);
        System.out.println("[ASR] got config ACK? " + ackOk);
        if (!ackOk) {
            ws.close(1000, "no-config-ack");
            throw new IllegalStateException("未收到配置响应：多半是配置包不被接受或鉴权失败");
        }
        boolean openedOk = opened.await(5, TimeUnit.SECONDS);
        System.out.println("[ASR] onOpen? " + openedOk);
        if (!openedOk) {
            ws.cancel();
            throw new IllegalStateException("WebSocket 未在 5s 内建立（检查 token / wsUrl / 网络）");
        }

        try {
            return finalTextFuture.get(overallTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            ws.close(1000, "timeout/exception");
            throw e;
        }
    }

    // —— 工具：构造帧（含自定义 4 字节头部区块*header_size=1*，与示例一致）——
    private static byte[] buildFrame(int messageType,
                                     int flags,
                                     int serialization,
                                     int compression,
                                     int sequence,
                                     byte[] payloadGz) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int headerSize = 1;

        // 头部
        out.write((byte) ((PROTOCOL_VERSION << 4) | headerSize));
        out.write((byte) ((messageType << 4) | flags));
        out.write((byte) ((serialization << 4) | compression));
        out.write((byte) 0x00); // reserved

        // 是否带序号（bit0 = 1）
        if ((flags & 0x01) == 0x01) {
            // 若是“最后一包”（bit1 = 1），序号必须为负
            int seqToWrite = ((flags & 0x02) == 0x02) ? -sequence : sequence;
            out.write(intToBytes(seqToWrite, true), 0, 4);
        }

        // payload size + payload
        out.write(intToBytes(payloadGz.length, true), 0, 4);
        try { out.write(payloadGz); } catch (IOException ignored) {}

        return out.toByteArray();
    }


    private static class ParseResult {
        boolean isLastPackage;
        int messageType;
        Object payloadMsg;
    }

    // —— 工具：解析服务端响应（对齐 Python parse_response）——
    private ParseResult parseResponse(byte[] res) throws IOException {
        ParseResult pr = new ParseResult();
        if (res == null || res.length < 4) return pr;

        int headerSize   = res[0] & 0x0f;
        int messageType  = (res[1] >> 4) & 0x0f;
        int flags        = res[1] & 0x0f;
        int serialization= (res[2] >> 4) & 0x0f;
        int compression  = res[2] & 0x0f;
        int pos = headerSize * 4;

        if ((flags & 0x01) == 0x01) pos += 4; // 跳过 sequence
        pr.isLastPackage = (flags & 0x02) == 0x02;
        pr.messageType = messageType;

        // 处理 payloadSize（兼容多类型）
        if (messageType == FULL_SERVER_RESPONSE) { pos += 4; }
        else if (messageType == SERVER_ACK) { pos += 4; if (pos + 4 <= res.length) pos += 4; }
        else if (messageType == SERVER_ERROR_RESPONSE) { pos += 8; }

        byte[] payload = java.util.Arrays.copyOfRange(res, pos, res.length);
        if (compression == GZIP_COMPRESSION) payload = gunzipSafe(payload);

        Object payloadMsg;
        if (serialization == JSON_SERIALIZATION) {
            try { payloadMsg = om.readValue(payload, Map.class); }
            catch (Exception e) { payloadMsg = new String(payload, "UTF-8"); }
        } else {
            payloadMsg = new String(payload, "UTF-8");
        }
        pr.payloadMsg = payloadMsg;

        // —— 调试打印 —— //
        String peek;
        try {
            String s = (payload.length > 0) ? new String(payload, "UTF-8") : "";
            peek = s.length() > 200 ? s.substring(0, 200) + "..." : s;
            peek = peek.replaceAll("\\s+", " ");
        } catch (Exception e) { peek = "<non-text>"; }
        AsrDbg.log("msg type=%d flags=%s last=%s payloadPeek=%s",
                messageType, Integer.toBinaryString(flags), pr.isLastPackage, peek);

        return pr;
    }




    private static byte[] gzip(byte[] input) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(bos);
        gzip.write(input);
        gzip.close();
        return bos.toByteArray();
    }

    private static byte[] gunzipSafe(byte[] input) {
        try (java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(input));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gis.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Exception e) {
            return input; // 解压失败则返回原始
        }
    }

    private static byte[] intToBytes(int v, boolean bigEndian) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        if (bigEndian) bb.putInt(v);
        else bb.order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(v);
        return bb.array();
    }

    private static int bytesToInt(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off+1] & 0xff) << 16) |
                ((b[off+2] & 0xff) << 8)  | (b[off+3] & 0xff);
    }

    private static boolean payloadIsFinal(Object payloadMsg) {
        if (!(payloadMsg instanceof Map)) return false;
        Map<?,?> m = (Map<?,?>) payloadMsg;

        Object v = firstNonNull(
                m.get("is_final"), m.get("final"), m.get("end"),
                m.get("isLast"), m.get("last"), m.get("eof"),
                (m.containsKey("status") ? ("end".equalsIgnoreCase(String.valueOf(m.get("status"))) ? true : null) : null)
        );
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        String s = String.valueOf(v);
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... xs) {
        for (T x : xs) if (x != null) return x;
        return null;
    }
}
