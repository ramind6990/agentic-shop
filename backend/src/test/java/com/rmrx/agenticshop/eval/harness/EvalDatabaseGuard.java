package com.rmrx.agenticshop.eval.harness;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Stops the eval context before the data source, Flyway or anything else starts, unless it points at a database whose
 * name ends with {@code _eval}: evals create conversations and runs, and must never write into the dev database.
 */
public final class EvalDatabaseGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        String url = environment.getProperty("spring.datasource.url", "");
        String database = url.replaceFirst("\\?.*$", "").replaceFirst("^.*/", "");
        if (!database.endsWith("_eval")) {
            throw new IllegalStateException("evals must run on a database named *_eval, not '" + database + "' (" + url
                    + "); set EVAL_DB_URL, and create it once: CREATE DATABASE agenticshop_eval OWNER agenticshop;");
        }
    }
}
