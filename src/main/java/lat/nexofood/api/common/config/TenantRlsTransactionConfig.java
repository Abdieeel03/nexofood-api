package lat.nexofood.api.common.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configura un interceptor AOP transaccional que invoca {@link TenantTransactionSynchronization}
 * al inicio de cada método o clase anotada con {@link Transactional}.
 */
@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class TenantRlsTransactionConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor tenantRlsTransactionAdvisor(
            ObjectProvider<TenantTransactionSynchronization> synchronizationProvider
    ) {
        Pointcut classPointcut = new AnnotationMatchingPointcut(Transactional.class, true);
        Pointcut methodPointcut = new AnnotationMatchingPointcut(null, Transactional.class, true);
        ComposablePointcut pointcut = new ComposablePointcut(classPointcut).union(methodPointcut);

        MethodInterceptor interceptor = invocation -> {
            Class<?> targetClass = invocation.getThis() != null ? invocation.getThis().getClass() : null;
            if (targetClass != null && !TenantConnectionPreparer.class.isAssignableFrom(targetClass)) {
                TenantTransactionSynchronization sync = synchronizationProvider.getIfAvailable();
                if (sync != null) {
                    sync.prepareCurrentTransactionIfNeeded();
                }
            }
            return invocation.proceed();
        };

        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
        advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
        return advisor;
    }
}
