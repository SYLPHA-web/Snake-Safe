package com.snakesafe.chat;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Written only when app.log-chats is true. Holds no user id, phone or IP. */
@Entity
@Table(name = "chat_logs")
public class ChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(length = 5)
    public String language;

    @Column(length = 1000)
    public String message;

    @Column(length = 4000)
    public String reply;

    @Column(nullable = false)
    public Instant createdAt;
}
