package com.vangelnum.ailingo.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Data
@Getter
@Slf4j
@Service
public class ValuesService {

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Value("${server.protocol}")
    private String protocol;

    @Value("${server.websocket.protocol}")
    private String webSocketProtocol;

    @Value("${server.host}")
    private String host;

    public ValuesService() {}

    public String getAddress() {
        return getDomain() + getContextPath();
    }

    public String getDomain() {
        return getProtocol() + "://" + getHost();
    }

    @PostConstruct
    public void init() {
        log.info("Link to swagger: " + getAddress() + "/swagger-ui/index.html");
    }
}