/**
 *    Copyright 2010-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.spring;

import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;
import static org.springframework.util.ObjectUtils.isEmpty;
import static org.springframework.util.StringUtils.hasLength;
import static org.springframework.util.StringUtils.tokenizeToStringArray;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.NestedIOException;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

/**
 * {@code FactoryBean} that creates an MyBatis {@code SqlSessionFactory}.
 * This is the usual way to set up a shared MyBatis {@code SqlSessionFactory} in a Spring application context;
 * the SqlSessionFactory can then be passed to MyBatis-based DAOs via dependency injection.
 *
 * Either {@code DataSourceTransactionManager} or {@code JtaTransactionManager} can be used for transaction
 * demarcation in combination with a {@code SqlSessionFactory}. JTA should be used for transactions
 * which span multiple databases or when container managed transactions (CMT) are being used.
 *
 * @author Putthiphong Boonphong
 * @author Hunter Presnall
 * @author Eduardo Macarron
 * @author Eddú Meléndez
 * @author Kazuki Shimizu
 *
 * @see #setConfigLocation
 * @see #setDataSource
 */

/**
 * spring-mybatis创建SqlSessionFactory定义的FactoryBean，他实现了InitializingBean接口
 */

public class SqlSessionFactoryBean implements FactoryBean<SqlSessionFactory>, InitializingBean, ApplicationListener<ApplicationEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqlSessionFactoryBean.class);
  /**mybatis配置文件资源*/
  private Resource configLocation;
  /**全局配置类*/
  private Configuration configuration;
  /**mapper文件对象*/
  private Resource[] mapperLocations;
  /**数据源对象*/
  private DataSource dataSource;

  private TransactionFactory transactionFactory;

  private Properties configurationProperties;

  private SqlSessionFactoryBuilder sqlSessionFactoryBuilder = new SqlSessionFactoryBuilder();
  /**affterPorperties方法中创建出来的DefaultSqlSessionFactory对象*/
  private SqlSessionFactory sqlSessionFactory;

  //EnvironmentAware requires spring 3.1
  private String environment = SqlSessionFactoryBean.class.getSimpleName();

  private boolean failFast;

  private Interceptor[] plugins;

  private TypeHandler<?>[] typeHandlers;

  private String typeHandlersPackage;

  private Class<?>[] typeAliases;

  private String typeAliasesPackage;

  private Class<?> typeAliasesSuperType;

  //issue #19. No default provider.
  private DatabaseIdProvider databaseIdProvider;

  private Class<? extends VFS> vfs;

  private Cache cache;

  private ObjectFactory objectFactory;

  private ObjectWrapperFactory objectWrapperFactory;

  /**
   * Sets the ObjectFactory.
   *
   * @since 1.1.2
   * @param objectFactory a custom ObjectFactory
   */
  public void setObjectFactory(ObjectFactory objectFactory) {
    this.objectFactory = objectFactory;
  }

  /**
   * Sets the ObjectWrapperFactory.
   *
   * @since 1.1.2
   * @param objectWrapperFactory a specified ObjectWrapperFactory
   */
  public void setObjectWrapperFactory(ObjectWrapperFactory objectWrapperFactory) {
    this.objectWrapperFactory = objectWrapperFactory;
  }

  /**
   * Gets the DatabaseIdProvider
   *
   * @since 1.1.0
   * @return a specified DatabaseIdProvider
   */
  public DatabaseIdProvider getDatabaseIdProvider() {
    return databaseIdProvider;
  }

  /**
   * Sets the DatabaseIdProvider.
   * As of version 1.2.2 this variable is not initialized by default.
   *
   * @since 1.1.0
   * @param databaseIdProvider a DatabaseIdProvider
   */
  public void setDatabaseIdProvider(DatabaseIdProvider databaseIdProvider) {
    this.databaseIdProvider = databaseIdProvider;
  }

  /**
   * Gets the VFS.
   * @return a specified VFS
   */
  public Class<? extends VFS> getVfs() {
    return this.vfs;
  }

  /**
   * Sets the VFS.
   * @param vfs a VFS
   */
  public void setVfs(Class<? extends VFS> vfs) {
    this.vfs = vfs;
  }

  /**
   * Gets the Cache.
   * @return a specified Cache
   */
  public Cache getCache() {
    return this.cache;
  }

  /**
   * Sets the Cache.
   * @param cache a Cache
   */
  public void setCache(Cache cache) {
    this.cache = cache;
  }

  /**
   * Mybatis plugin list.
   *
   * @since 1.0.1
   *
   * @param plugins list of plugins
   *
   */
  public void setPlugins(Interceptor[] plugins) {
    this.plugins = plugins;
  }

  /**
   * Packages to search for type aliases.
   *
   * @since 1.0.1
   *
   * @param typeAliasesPackage package to scan for domain objects
   *
   */
  public void setTypeAliasesPackage(String typeAliasesPackage) {
    this.typeAliasesPackage = typeAliasesPackage;
  }

  /**
   * Super class which domain objects have to extend to have a type alias created.
   * No effect if there is no package to scan configured.
   *
   * @since 1.1.2
   *
   * @param typeAliasesSuperType super class for domain objects
   *
   */
  public void setTypeAliasesSuperType(Class<?> typeAliasesSuperType) {
    this.typeAliasesSuperType = typeAliasesSuperType;
  }

  /**
   * Packages to search for type handlers.
   *
   * @since 1.0.1
   *
   * @param typeHandlersPackage package to scan for type handlers
   *
   */
  public void setTypeHandlersPackage(String typeHandlersPackage) {
    this.typeHandlersPackage = typeHandlersPackage;
  }

  /**
   * Set type handlers. They must be annotated with {@code MappedTypes} and optionally with {@code MappedJdbcTypes}
   *
   * @since 1.0.1
   *
   * @param typeHandlers Type handler list
   */
  public void setTypeHandlers(TypeHandler<?>[] typeHandlers) {
    this.typeHandlers = typeHandlers;
  }

  /**
   * List of type aliases to register. They can be annotated with {@code Alias}
   *
   * @since 1.0.1
   *
   * @param typeAliases Type aliases list
   */
  public void setTypeAliases(Class<?>[] typeAliases) {
    this.typeAliases = typeAliases;
  }

  /**
   * If true, a final check is done on Configuration to assure that all mapped
   * statements are fully loaded and there is no one still pending to resolve
   * includes. Defaults to false.
   *
   * @since 1.0.1
   *
   * @param failFast enable failFast
   */
  public void setFailFast(boolean failFast) {
    this.failFast = failFast;
  }

  /**
   * Set the location of the MyBatis {@code SqlSessionFactory} config file. A typical value is
   * "WEB-INF/mybatis-configuration.xml".
   *
   * @param configLocation a location the MyBatis config file
   */
  public void setConfigLocation(Resource configLocation) {
    this.configLocation = configLocation;
  }

  /**
   * Set a customized MyBatis configuration.
   * @param configuration MyBatis configuration
   * @since 1.3.0
   */
  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  /**
   * Set locations of MyBatis mapper files that are going to be merged into the {@code SqlSessionFactory}
   * configuration at runtime.
   *
   * This is an alternative to specifying "&lt;sqlmapper&gt;" entries in an MyBatis config file.
   * This property being based on Spring's resource abstraction also allows for specifying
   * resource patterns here: e.g. "classpath*:sqlmap/*-mapper.xml".
   *
   * @param mapperLocations location of MyBatis mapper files
   */
  public void setMapperLocations(Resource[] mapperLocations) {
    this.mapperLocations = mapperLocations;
  }

  /**
   * Set optional properties to be passed into the SqlSession configuration, as alternative to a
   * {@code &lt;properties&gt;} tag in the configuration xml file. This will be used to
   * resolve placeholders in the config file.
   *
   * @param  sqlSessionFactoryProperties optional properties for the SqlSessionFactory
   */
  public void setConfigurationProperties(Properties sqlSessionFactoryProperties) {
    this.configurationProperties = sqlSessionFactoryProperties;
  }

  /**
   * Set the JDBC {@code DataSource} that this instance should manage transactions for. The {@code DataSource}
   * should match the one used by the {@code SqlSessionFactory}: for example, you could specify the same
   * JNDI DataSource for both.
   *
   * A transactional JDBC {@code Connection} for this {@code DataSource} will be provided to application code
   * accessing this {@code DataSource} directly via {@code DataSourceUtils} or {@code DataSourceTransactionManager}.
   *
   * The {@code DataSource} specified here should be the target {@code DataSource} to manage transactions for, not
   * a {@code TransactionAwareDataSourceProxy}. Only data access code may work with
   * {@code TransactionAwareDataSourceProxy}, while the transaction manager needs to work on the
   * underlying target {@code DataSource}. If there's nevertheless a {@code TransactionAwareDataSourceProxy}
   * passed in, it will be unwrapped to extract its target {@code DataSource}.
   *
   * @param dataSource a JDBC {@code DataSource}
   *
   */
  public void setDataSource(DataSource dataSource) {
    if (dataSource instanceof TransactionAwareDataSourceProxy) {
      // If we got a TransactionAwareDataSourceProxy, we need to perform
      // transactions for its underlying target DataSource, else data
      // access code won't see properly exposed transactions (i.e.
      // transactions for the target DataSource).
      this.dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
    } else {
      this.dataSource = dataSource;
    }
  }

  /**
   * Sets the {@code SqlSessionFactoryBuilder} to use when creating the {@code SqlSessionFactory}.
   *
   * This is mainly meant for testing so that mock SqlSessionFactory classes can be injected. By
   * default, {@code SqlSessionFactoryBuilder} creates {@code DefaultSqlSessionFactory} instances.
   *
   * @param sqlSessionFactoryBuilder a SqlSessionFactoryBuilder
   *
   */
  public void setSqlSessionFactoryBuilder(SqlSessionFactoryBuilder sqlSessionFactoryBuilder) {
    this.sqlSessionFactoryBuilder = sqlSessionFactoryBuilder;
  }

  /**
   * Set the MyBatis TransactionFactory to use. Default is {@code SpringManagedTransactionFactory}
   *
   * The default {@code SpringManagedTransactionFactory} should be appropriate for all cases:
   * be it Spring transaction management, EJB CMT or plain JTA. If there is no active transaction,
   * SqlSession operations will execute SQL statements non-transactionally.
   *
   * <b>It is strongly recommended to use the default {@code TransactionFactory}.</b> If not used, any
   * attempt at getting an SqlSession through Spring's MyBatis framework will throw an exception if
   * a transaction is active.
   *
   * @see SpringManagedTransactionFactory
   * @param transactionFactory the MyBatis TransactionFactory
   */
  public void setTransactionFactory(TransactionFactory transactionFactory) {
    this.transactionFactory = transactionFactory;
  }

  /**
   * <b>NOTE:</b> This class <em>overrides</em> any {@code Environment} you have set in the MyBatis
   * config file. This is used only as a placeholder name. The default value is
   * {@code SqlSessionFactoryBean.class.getSimpleName()}.
   *
   * @param environment the environment name
   */
  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void afterPropertiesSet() throws Exception {
    notNull(dataSource, "Property 'dataSource' is required");
    notNull(sqlSessionFactoryBuilder, "Property 'sqlSessionFactoryBuilder' is required");
    state((configuration == null && configLocation == null) || !(configuration != null && configLocation != null),
              "Property 'configuration' and 'configLocation' can not specified with together");
    /**创建sqlSessionFactory*/
    this.sqlSessionFactory = buildSqlSessionFactory();
  }

  /**
   * Build a {@code SqlSessionFactory} instance.
   *
   * The default implementation uses the standard MyBatis {@code XMLConfigBuilder} API to build a
   * {@code SqlSessionFactory} instance based on an Reader.
   * Since 1.3.0, it can be specified a {@link Configuration} instance directly(without config file).
   *
   * @return SqlSessionFactory
   * @throws IOException if loading the config file failed
   */
  /**
   * 创建DefaultSqlSessionFactory
   * @return
   * @throws IOException
   */
  protected SqlSessionFactory buildSqlSessionFactory() throws IOException {

    Configuration configuration;

    XMLConfigBuilder xmlConfigBuilder = null;
    /**全局配置类不为空，一般都为空*/
    if (this.configuration != null) {
      configuration = this.configuration;
      if (configuration.getVariables() == null) {
        configuration.setVariables(this.configurationProperties);
      } else if (this.configurationProperties != null) {
        configuration.getVariables().putAll(this.configurationProperties);
      }
    }
    /**根据配置资源文件创建配置类*/
    else if (this.configLocation != null) {
      /**创建XmlConfigBuilder类*/
      xmlConfigBuilder = new XMLConfigBuilder(this.configLocation.getInputStream(), null, this.configurationProperties);
      /**获取全局配置类*/
      configuration = xmlConfigBuilder.getConfiguration();
    } else {
      LOGGER.debug(() -> "Property 'configuration' or 'configLocation' not specified, using default MyBatis Configuration");
      configuration = new Configuration();
      if (this.configurationProperties != null) {
        configuration.setVariables(this.configurationProperties);
      }
    }
    /**设置ObjectFactory，这个可以自定义，特别是分页拦截器的时候，就需要自定义*/
    if (this.objectFactory != null) {
      configuration.setObjectFactory(this.objectFactory);
    }
    /**设置数据转换工厂*/
    if (this.objectWrapperFactory != null) {
      configuration.setObjectWrapperFactory(this.objectWrapperFactory);
    }

    if (this.vfs != null) {
      configuration.setVfsImpl(this.vfs);
    }
    /**根据包名注册别名*/
    if (hasLength(this.typeAliasesPackage)) {
      String[] typeAliasPackageArray = tokenizeToStringArray(this.typeAliasesPackage,
          ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
      for (String packageToScan : typeAliasPackageArray) {
        configuration.getTypeAliasRegistry().registerAliases(packageToScan,
                typeAliasesSuperType == null ? Object.class : typeAliasesSuperType);
        LOGGER.debug(() -> "Scanned package: '" + packageToScan + "' for aliases");
      }
    }
    /**根据单个typeAlias注册别名*/
    if (!isEmpty(this.typeAliases)) {
      for (Class<?> typeAlias : this.typeAliases) {
        configuration.getTypeAliasRegistry().registerAlias(typeAlias);
        LOGGER.debug(() -> "Registered type alias: '" + typeAlias + "'");
      }
    }
    /**注册拦截器*/
    if (!isEmpty(this.plugins)) {
      for (Interceptor plugin : this.plugins) {
        configuration.addInterceptor(plugin);
        LOGGER.debug(() -> "Registered plugin: '" + plugin + "'");
      }
    }
    /**根据基础包注册TypeHandler*/
    if (hasLength(this.typeHandlersPackage)) {
      String[] typeHandlersPackageArray = tokenizeToStringArray(this.typeHandlersPackage,
          ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
      for (String packageToScan : typeHandlersPackageArray) {
        configuration.getTypeHandlerRegistry().register(packageToScan);
        LOGGER.debug(() -> "Scanned package: '" + packageToScan + "' for type handlers");
      }
    }
    /**根据TypeHandlers属性注册TypeHandler*/
    if (!isEmpty(this.typeHandlers)) {
      for (TypeHandler<?> typeHandler : this.typeHandlers) {
        configuration.getTypeHandlerRegistry().register(typeHandler);
        LOGGER.debug(() -> "Registered type handler: '" + typeHandler + "'");
      }
    }

    if (this.databaseIdProvider != null) {//fix #64 set databaseId before parse mapper xmls
      try {
        configuration.setDatabaseId(this.databaseIdProvider.getDatabaseId(this.dataSource));
      } catch (SQLException e) {
        throw new NestedIOException("Failed getting a databaseId", e);
      }
    }

    if (this.cache != null) {
      configuration.addCache(this.cache);
    }

    if (xmlConfigBuilder != null) {
      try {
        /**解析mybatis配置文件的各项配置，为Configuration赋值*/
        xmlConfigBuilder.parse();
        LOGGER.debug(() -> "Parsed configuration file: '" + this.configLocation + "'");
      } catch (Exception ex) {
        throw new NestedIOException("Failed to parse config resource: " + this.configLocation, ex);
      } finally {
        ErrorContext.instance().reset();
      }
    }
    /**默认事务工厂为SpringManagedTransactionFactory*/
    if (this.transactionFactory == null) {
      this.transactionFactory = new SpringManagedTransactionFactory();
    }
    /**设置环境，需要注意的是这里的事务工厂很重要是SpringManagedTransactionFactory
     * 这个在后面DefaultSqlSession中获取Connection中用到，这个事务工厂会先从当前线程中获取Connection
     * 也就是事务中，和事务的关联也就是在这里体现的*/
    configuration.setEnvironment(new Environment(this.environment, this.transactionFactory, this.dataSource));

    if (!isEmpty(this.mapperLocations)) {
      for (Resource mapperLocation : this.mapperLocations) {
        if (mapperLocation == null) {
          continue;
        }
        /**解析mapper文件*/
        try {
          XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(mapperLocation.getInputStream(),
              configuration, mapperLocation.toString(), configuration.getSqlFragments());
          xmlMapperBuilder.parse();
        } catch (Exception e) {
          throw new NestedIOException("Failed to parse mapping resource: '" + mapperLocation + "'", e);
        } finally {
          ErrorContext.instance().reset();
        }
        LOGGER.debug(() -> "Parsed mapper file: '" + mapperLocation + "'");
      }
    } else {
      LOGGER.debug(() -> "Property 'mapperLocations' was not specified or no matching resources found");
    }

    return this.sqlSessionFactoryBuilder.build(configuration);
  }

  /**
   * FactoryBean的getObject方法，获取FactoryBean的真正对象，也就是DefaultSqlSessionFactory
   * {@inheritDoc}
   */
  @Override
  public SqlSessionFactory getObject() throws Exception {
    if (this.sqlSessionFactory == null) {
      afterPropertiesSet();
    }

    return this.sqlSessionFactory;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<? extends SqlSessionFactory> getObjectType() {
    return this.sqlSessionFactory == null ? SqlSessionFactory.class : this.sqlSessionFactory.getClass();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSingleton() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void onApplicationEvent(ApplicationEvent event) {
    if (failFast && event instanceof ContextRefreshedEvent) {
      // fail-fast -> check all statements are completed
      this.sqlSessionFactory.getConfiguration().getMappedStatementNames();
    }
  }

}