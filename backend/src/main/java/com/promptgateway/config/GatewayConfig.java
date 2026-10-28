package com.promptgateway.config;

import com.promptgateway.filter.PatternFilter;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.gateway.InputValidator;
import com.promptgateway.judge.BlockDecider;
import com.promptgateway.judge.Judge;
import com.promptgateway.judge.QuestionSet;
import com.promptgateway.judge.jev.JevJudge;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.service.AnswerGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 게이트웨이를 구성하는 부품들을 설정 값으로 만들어서 서로 연결한다.
 * 설정이 잘못되었으면(잘못된 정규식, 없는 질문 파일 등) 부품을 만드는 단계에서 예외가 나므로 서버가 시작되지 않는다.
 */
@Configuration
@EnableConfigurationProperties({GatewayProperties.class, JudgeProperties.class})
public class GatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean
    public InputValidator inputValidator(GatewayProperties gateway) {
        return new InputValidator(gateway.maxPromptLength());
    }

    @Bean
    public PatternFilter patternFilter(GatewayProperties gateway) {
        return new PatternFilter(gateway.patterns());
    }

    @Bean
    public QuestionSet questionSet(JudgeProperties judge) {
        return QuestionSet.fromClasspath(judge.questionsFile());
    }

    /** Jev 호출용 HTTP 클라이언트. 연결과 읽기 타임아웃을 설정으로 건다. */
    @Bean
    public RestClient jevRestClient(JudgeProperties judge) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(judge.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(judge.readTimeout());
        return RestClient.builder().baseUrl(judge.baseUrl()).requestFactory(factory).build();
    }

    @Bean
    public Judge judge(RestClient jevRestClient, QuestionSet questionSet, JudgeProperties judge) {
        return new JevJudge(jevRestClient, questionSet, judge.model(), judge.apiKey(),
                judge.maxRetries(), judge.retryBackoff(), Thread::sleep);
    }

    @Bean
    public BlockDecider blockDecider(QuestionSet questionSet, JudgeProperties judge) {
        return new BlockDecider(questionSet.ids(), judge.threshold());
    }

    /** 요청별 한 줄 로그. 프롬프트 원문 저장은 기본이 꺼짐이고, 시험 환경에서만 켠다(AC-7.3, 7.4). */
    @Bean
    public GatewayLogger gatewayLogger(@Value("${logging.store-raw-prompt:false}") boolean storeRawPrompt) {
        return new GatewayLogger(storeRawPrompt);
    }

    @Bean
    public ChatGateway chatGateway(InputValidator validator, PatternFilter patternFilter, Judge judge,
                                   BlockDecider blockDecider, AnswerGenerator answerGenerator,
                                   GatewayLogger gatewayLogger, GatewayProperties gateway) {
        if (gateway.measurementMode()) {
            log.warn("측정 모드가 켜져 있습니다. 응답 모델을 호출하지 않고 판정 결과를 응답에 포함합니다. 사용자에게 노출하는 환경에서는 꺼야 합니다.");
        }
        return new ChatGateway(validator, patternFilter, judge, blockDecider, answerGenerator, gatewayLogger,
                gateway.measurementMode());
    }
}
