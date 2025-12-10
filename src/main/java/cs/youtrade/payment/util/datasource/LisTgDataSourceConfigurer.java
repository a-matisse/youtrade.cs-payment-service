package cs.youtrade.payment.util.datasource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Objects;

import static cs.youtrade.payment.util.datasource.CustomHibernatePropertiesConfig.hibernateProperties;

@Configuration
@EnableJpaRepositories(
        basePackages = "cs.youtrade.payment",
        entityManagerFactoryRef = "lisTgEntityManagerFactory",
        transactionManagerRef = "lisTgTransactionManager"
)
public class LisTgDataSourceConfigurer {

    @Bean
    @ConfigurationProperties("spring.datasource.listg")
    public DataSourceProperties lisTgDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource lisTgDataSource() {
        return lisTgDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate lisTgJdbcTemplate(@Qualifier("lisTgDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean lisTgEntityManagerFactory(
            @Qualifier("lisTgDataSource") DataSource dataSource,
            EntityManagerFactoryBuilder builder
    ) {
        return builder
                .dataSource(dataSource)
                .packages("cs.youtrade.bot.telegram")
                .properties(hibernateProperties())
                .build();
    }

    @Bean
    public PlatformTransactionManager lisTgTransactionManager(
            @Qualifier("lisTgEntityManagerFactory") LocalContainerEntityManagerFactoryBean lisTgEntityManagerFactory
    ) {
        return new JpaTransactionManager(Objects.requireNonNull(lisTgEntityManagerFactory.getObject()));
    }
}
