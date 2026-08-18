package org.lpu.dev.codes.helpdesk.config;

import java.util.Properties;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.Employee;
import org.lpu.dev.codes.helpdesk.model.OtpCode;
import org.lpu.dev.codes.helpdesk.model.PasswordResetToken;
import org.lpu.dev.codes.helpdesk.model.QueueCounter;
import org.lpu.dev.codes.helpdesk.model.QueueTransferRequest;
import org.lpu.dev.codes.helpdesk.model.Student;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketCategoryDefinition;
import org.lpu.dev.codes.helpdesk.model.TicketCsm;
import org.lpu.dev.codes.helpdesk.model.TicketMessage;
import org.lpu.dev.codes.helpdesk.model.TicketMessageRead;
import org.lpu.dev.codes.helpdesk.model.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class HibernateConfig {

    @Value("${spring.jpa.properties.hibernate.dialect:org.hibernate.dialect.PostgreSQLDialect}")
    private String hibernateDialect;

    @Value("${spring.jpa.hibernate.ddl-auto:validate}")
    private String ddlAuto;

    @Bean
    @Primary
    @DependsOn("schemaMigrator")
    public LocalSessionFactoryBean sessionFactory(DataSource dataSource) {
        LocalSessionFactoryBean factoryBean = new LocalSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setAnnotatedClasses(
                User.class,
                OtpCode.class,
                PasswordResetToken.class,
                Ticket.class,
                TicketMessage.class,
                TicketMessageRead.class,
                QueueCounter.class,
                QueueTransferRequest.class,
                TicketCategoryDefinition.class,
                TicketCsm.class
        );
        factoryBean.setHibernateProperties(hibernateProperties(ddlAuto));
        return factoryBean;
    }

    @Bean
    public LocalSessionFactoryBean gateSessionFactory(
            @Qualifier("gateDataSource") DataSource gateDataSource
    ) {
        LocalSessionFactoryBean factoryBean = new LocalSessionFactoryBean();
        factoryBean.setDataSource(gateDataSource);
        factoryBean.setAnnotatedClasses(Student.class, Employee.class);
        // Gate schema is owned by the gate system. This app only writes lpu_email
        // when MISD encodes an address onto a student/employee record.
        factoryBean.setHibernateProperties(hibernateProperties("none"));
        return factoryBean;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(SessionFactory sessionFactory) {
        return new HibernateTransactionManager(sessionFactory);
    }

    @Bean
    public PlatformTransactionManager gateTransactionManager(
            @Qualifier("gateSessionFactory") SessionFactory gateSessionFactory
    ) {
        return new HibernateTransactionManager(gateSessionFactory);
    }

    private Properties hibernateProperties(String hbm2ddl) {
        Properties properties = new Properties();
        properties.put("hibernate.dialect", hibernateDialect);
        properties.put("hibernate.hbm2ddl.auto", hbm2ddl);
        properties.put("hibernate.show_sql", "false");
        properties.put("hibernate.format_sql", "true");
        properties.put("hibernate.jdbc.time_zone", "UTC");
        return properties;
    }
}
