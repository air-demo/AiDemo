package com.air.demotest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "asr.qiniu")
public class QiniuAsrProps {
    private String token;
    private String wsUrl;
    private int segDurationMs = 300;
    private int sampleRate = 16000;
    private int channels = 1;
    private int bits = 16;
    private String codec = "raw";
    private String modelName = "asr";
    private boolean enablePunc = true;
    private int connectTimeoutMs = 8000;
    private int readTimeoutMs = 15000;
    private int overallTimeoutMs = 25000;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }

    public int getSegDurationMs() {
        return segDurationMs;
    }

    public void setSegDurationMs(int segDurationMs) {
        this.segDurationMs = segDurationMs;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    public int getBits() {
        return bits;
    }

    public void setBits(int bits) {
        this.bits = bits;
    }

    public String getCodec() {
        return codec;
    }

    public void setCodec(String codec) {
        this.codec = codec;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public boolean isEnablePunc() {
        return enablePunc;
    }

    public void setEnablePunc(boolean enablePunc) {
        this.enablePunc = enablePunc;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getOverallTimeoutMs() {
        return overallTimeoutMs;
    }

    public void setOverallTimeoutMs(int overallTimeoutMs) {
        this.overallTimeoutMs = overallTimeoutMs;
    }


}
