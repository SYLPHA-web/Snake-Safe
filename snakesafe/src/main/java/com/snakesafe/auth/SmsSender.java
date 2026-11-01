package com.snakesafe.auth;

public interface SmsSender {

    void send(String phone, String text);
}
