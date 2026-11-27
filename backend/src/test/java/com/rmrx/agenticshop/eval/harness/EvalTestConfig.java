package com.rmrx.agenticshop.eval.harness;

import com.rmrx.agenticshop.ai.model.AiModelConfig;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test-only wiring of the evals: the database guard, and the agent's chat model wrapped in a
 * {@link RecordingChatModel}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EvalTestConfig {

    @Bean
    static EvalDatabaseGuard evalDatabaseGuard() {
        return new EvalDatabaseGuard();
    }

    @Bean
    static BeanPostProcessor recordingChatModelPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (AiModelConfig.CHAT_MODEL.equals(beanName) && bean instanceof ChatModel model
                        && !(bean instanceof RecordingChatModel)) {
                    return new RecordingChatModel(model);
                }
                return bean;
            }
        };
    }
}
