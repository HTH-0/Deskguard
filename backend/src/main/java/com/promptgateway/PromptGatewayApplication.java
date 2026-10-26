package com.promptgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 이 클래스 하나가 애플리케이션의 시작점(entry point)이다.
 * main() 을 실행하면 내장된 톰캣 서버가 뜨고, application.yml에 적힌 포트(기본 8080)로
 * 요청을 받을 준비가 된다.
 *
 * @SpringBootApplication 어노테이션 하나가 사실 3개를 합쳐놓은 것이다:
 *   - @Configuration: 이 클래스가 설정 정보를 담을 수 있음
 *   - @EnableAutoConfiguration: 클래스패스에 있는 라이브러리를 보고 스프링이 알아서 필요한 설정을 채워줌
 *   - @ComponentScan: 이 클래스가 있는 패키지(com.promptgateway) 하위를 뒤져서
 *                     @RestController, @Service, @Configuration 붙은 클래스를 자동으로 찾아 등록함
 *     -> 그래서 ChatController, GeminiService 등을 따로 등록하는 코드를 안 써도 자동으로 인식된다.
 */
@SpringBootApplication
public class PromptGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromptGatewayApplication.class, args);
    }
}
