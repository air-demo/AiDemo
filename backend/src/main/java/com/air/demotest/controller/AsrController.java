package com.air.demotest.controller;

import com.air.demotest.config.QiniuAsrProps;
import com.air.demotest.utils.QiniuAsrClient;
import org.apache.commons.io.IOUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.InputStream;


@CrossOrigin
@RestController
@RequestMapping("/api")
public class AsrController {

    private final QiniuAsrProps props;
    private QiniuAsrClient client;

    public AsrController(QiniuAsrProps props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        this.client = new QiniuAsrClient(props);
    }

    @PostMapping(path = "/asr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> asr(@RequestPart("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("no file");
        }

        // 1) 读取整段 WAV
        byte[] wav;
        try (InputStream in = file.getInputStream()) {
            wav = IOUtils.toByteArray(in);
        }

        if (wav.length < 44) {
            return ResponseEntity.badRequest().body("invalid wav");
        }

        // 2) 跳过典型 44 字节 WAV 头，取裸 PCM LE16
        //    （前端已确保 16k/mono/16bit。如果你不确定 WAV 头大小，建议改用 javax.sound.sampled 转码）
        byte[] pcm = new byte[wav.length - 44];
        System.arraycopy(wav, 44, pcm, 0, pcm.length);

        // 3) 调用 ASR
        String text;
        try {
            text = client.recognizePcm(pcm);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(502).body("ASR error: " + e.getMessage());
        }

        return ResponseEntity.ok(text == null ? "" : text);
    }
}