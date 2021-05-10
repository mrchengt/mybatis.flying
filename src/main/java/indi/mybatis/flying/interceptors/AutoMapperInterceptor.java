/**
 * 
 * @date 2019年12月18日 11:56:08
 *
 * @author 李萌
 * @email limeng32@live.cn
 * @since JDK 1.8
 * @description The SQL statement is automatically generated by intercepting the
 *              StatementHandler's prepare method, according to the annotation
 *              information configured by the parameter parameterObject.
 */
package indi.mybatis.flying.interceptors;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.scripting.xmltags.ForEachSqlNode;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import indi.mybatis.flying.builders.SqlBuilder;
import indi.mybatis.flying.exception.AutoMapperException;
import indi.mybatis.flying.exception.AutoMapperExceptionEnum;
import indi.mybatis.flying.models.Conditionable;
import indi.mybatis.flying.models.FlyingModel;
import indi.mybatis.flying.models.LoggerDescriptionable;
import indi.mybatis.flying.statics.ActionType;
import indi.mybatis.flying.utils.FlyingManager;
import indi.mybatis.flying.utils.LogLevel;

@Intercepts({
		@Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class, Integer.class }) })
public class AutoMapperInterceptor implements Interceptor {
	private static String dialectValue = "";
	private LogLevel logLevel;

	private static final Logger logger = LoggerFactory.getLogger(AutoMapperInterceptor.class);
	private static final ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();
	private static final ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();
	private static final ReflectorFactory DEFAULT_REFLECTOR_FACTORY = new DefaultReflectorFactory();

	private static final String DIALECT = "dialect";
	private static final String LOG_LEVEL = "logLevel";
	private static final String LOGGER_DESCRIPTION = "loggerDescription";

	private static final String DELEGATE_BOUNDSQL_SQL = "delegate.boundSql.sql";
	private static final String DELEGATE_BOUNDSQL_PARAMETEROBJECT = "delegate.boundSql.parameterObject";
	private static final String DELEGATE_BOUNDSQL_PARAMETERMAPPINGS = "delegate.boundSql.parameterMappings";
	private static final String DELEGATE_CONFIGURATION = "delegate.configuration";
	private static final String DELEGATE_MAPPEDSTATEMENT = "delegate.mappedStatement";

	private static final String LIMIT_1 = " limit 1";

	private static final String MYSQL = "mysql";

	private static final String H2 = "h2";

	private static boolean isMysqlOrH2;

	private static boolean isMysql;

	private SqlSourceBuilder builder;

	private LoggerDescriptionable loggerDescriptionHandler;

	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
		MetaObject metaStatementHandler = getRealObj(statementHandler);
		String originalSql = (String) metaStatementHandler.getValue(DELEGATE_BOUNDSQL_SQL);
		Configuration configuration = (Configuration) metaStatementHandler.getValue(DELEGATE_CONFIGURATION);
		Object parameterObject = metaStatementHandler.getValue(DELEGATE_BOUNDSQL_PARAMETEROBJECT);
		MappedStatement mappedStatement = (MappedStatement) metaStatementHandler.getValue(DELEGATE_MAPPEDSTATEMENT);
		FlyingModel flyingModel = FlyingManager.fetchFlyingFeatureNew(originalSql, configuration, mappedStatement);
//		System.out.println("flyingModel::" + JSONObject.toJSONString(flyingModel));
		if (flyingModel.isHasFlyingFeature()) {
			boolean needHandleLimiterAndSorter = false;
			String newSql = null;
			switch (flyingModel.getActionType()) {
			case COUNT:
				newSql = SqlBuilder.buildCountSql(parameterObject, flyingModel);
				break;
			case DELETE:
				newSql = SqlBuilder.buildDeleteSql(parameterObject);
				break;
			case INSERT:
				newSql = SqlBuilder.buildInsertSql(parameterObject, flyingModel);
				break;
			case INSERT_BATCH:
				newSql = SqlBuilder.buildInsertBatchSql(parameterObject, flyingModel);
				break;
			case SELECT:
				newSql = SqlBuilder.buildSelectSql(mappedStatement.getResultMaps().get(0).getType(), flyingModel);
//				needHandleLimiterAndSorter = true;
				break;
			case SELECT_ALL:
				newSql = SqlBuilder.buildSelectAllSql(parameterObject, flyingModel);
				needHandleLimiterAndSorter = true;
				break;
			case SELECT_ONE:
				newSql = SqlBuilder.buildSelectOneSql(parameterObject, flyingModel);
//				needHandleLimiterAndSorter = true;
				break;
			case UPDATE:
				newSql = SqlBuilder.buildUpdateSql(parameterObject, flyingModel);
				break;
			case UPDATE_BATCH:
				newSql = SqlBuilder.buildUpdateBatchSql(parameterObject, flyingModel);
				break;
			case UPDATE_PERSISTENT:
				newSql = SqlBuilder.buildUpdatePersistentSql(parameterObject, flyingModel);
				break;
			default:
				break;
			}
			if (!LogLevel.NONE.equals(logLevel)) {
				log(logger, logLevel, new StringBuilder("Auto generated sql: ").append(newSql).toString());
			}
			SqlSource sqlSource = buildSqlSource(configuration, newSql, parameterObject.getClass());
			List<ParameterMapping> parameterMappings = sqlSource.getBoundSql(parameterObject).getParameterMappings();
			metaStatementHandler.setValue(DELEGATE_BOUNDSQL_SQL, sqlSource.getBoundSql(parameterObject).getSql());
			metaStatementHandler.setValue(DELEGATE_BOUNDSQL_PARAMETERMAPPINGS, parameterMappings);

			if (loggerDescriptionHandler != null) {
				LogLevel loggerLevel = loggerDescriptionHandler.getLogLevel(flyingModel.getId());
				if (!LogLevel.NONE.equals(loggerLevel)) {
					BoundSql boundSqlTemp = statementHandler.getBoundSql();
					String sqlTemp = boundSqlTemp.getSql();
					StringBuilder stringBuilder = new StringBuilder();
					stringBuilder.append("Method: ").append(flyingModel.getId()).append("\r\n");
					stringBuilder.append("Bound sql: ").append(sqlTemp).append("\r\n");
					if (ActionType.SELECT.equals(flyingModel.getActionType())) {
						stringBuilder.append("Bound value: {").append(parameterMappings.get(0).getProperty())
								.append("=").append(parameterObject).append("};");
					} else {
						MetaObject metaObject = parameterObject == null ? null
								: configuration.newMetaObject(parameterObject);
						stringBuilder.append("Bound value: {");
						boolean b = true;
						for (ParameterMapping parameterMapping : parameterMappings) {
							if (parameterMapping.getMode() != ParameterMode.OUT) {
								Object value;
								String propertyName = parameterMapping.getProperty();
								value = metaObject == null ? null : metaObject.getValue(propertyName);
								if (b) {
									b = false;
								} else {
									stringBuilder.append(", ");
								}
								stringBuilder.append(propertyName).append("=").append(value);
							}
						}
						stringBuilder.append("};");
					}
					log(logger, loggerLevel, stringBuilder.toString());
				}
			}
			/* Start dealing with paging */
			if (needHandleLimiterAndSorter && (parameterObject instanceof Conditionable)
					&& (invocation.getTarget() instanceof RoutingStatementHandler)) {
				Conditionable condition = (Conditionable) parameterObject;
				if (condition.getLimiter() != null) {
					BoundSql boundSql = statementHandler.getBoundSql();
					String sql = boundSql.getSql();
					Connection connection = (Connection) invocation.getArgs()[0];
					String countSql = new StringBuilder("select count(0) from (").append(sql).append(") myCount")
							.toString();
					PreparedStatement countStmt = connection.prepareStatement(countSql);
					BoundSql countBS = new BoundSql(mappedStatement.getConfiguration(), countSql,
							boundSql.getParameterMappings(), parameterObject);
					setParameters(countStmt, mappedStatement, countBS, parameterObject);
					ResultSet rs = countStmt.executeQuery();
					try {
						int count = 0;
						if (rs.next()) {
							count = rs.getInt(1);
						}
						condition.getLimiter().setTotalCount(count);
					} finally {
						rs.close();
						countStmt.close();
					}
					String pageSql = generatePageSql(sql, condition);
					metaStatementHandler.setValue(DELEGATE_BOUNDSQL_SQL, pageSql);
				} else if (condition.getSorter() != null) {
					BoundSql boundSql = statementHandler.getBoundSql();
					String sql = boundSql.getSql();
					String pageSql = generatePageSql(sql, condition);
					metaStatementHandler.setValue(DELEGATE_BOUNDSQL_SQL, pageSql);
				}
			}
		} else if (loggerDescriptionHandler != null) {
			LogLevel loggerLevel = loggerDescriptionHandler.getLogLevel(mappedStatement.getId());
			if (!LogLevel.NONE.equals(loggerLevel)) {
				String sqlTemp = statementHandler.getBoundSql().getSql();
				StringBuilder stringBuilder = new StringBuilder();
				stringBuilder.append("Method: ").append(mappedStatement.getId()).append("\r\n");
				stringBuilder.append("Bound sql: ").append(sqlTemp).append("\r\n");
				stringBuilder.append("Bound value: ").append(parameterObject).append(";");
				log(logger, loggerLevel, stringBuilder.toString());
			}
		}

		/*
		 * Call the original statementHandler's prepare method to complete the original
		 * logic.
		 */
//		statementHandler = (StatementHandler) metaStatementHandler.getOriginalObject();
//		statementHandler.prepare((Connection) invocation.getArgs()[0], mappedStatement.getTimeout());
		/* Pass to the next interceptor. */
		return invocation.proceed();
	}

	private static void log(Logger logger, LogLevel level, String log) {
		switch (level) {
		case ERROR:
			logger.error(log);
			break;
		case WARN:
			logger.warn(log);
			break;
		case INFO:
			logger.info(log);
			break;
		case DEBUG:
			logger.debug(log);
			break;
		case TRACE:
			logger.trace(log);
			break;
		case FATAL:
			logger.error(new StringBuilder("!!!FATAL!!! ").append(log).toString());
			break;
		default:
			break;
		}
	}

	private MetaObject getRealObj(Object obj) {
		MetaObject metaStatement = MetaObject.forObject(obj, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY,
				DEFAULT_REFLECTOR_FACTORY);
		/* Separating the proxy object chain */
		while (metaStatement.hasGetter("h")) {
			Object object = metaStatement.getValue("h");
			metaStatement = MetaObject.forObject(object, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY,
					DEFAULT_REFLECTOR_FACTORY);
		}
		/* Isolate the target class of the last proxy object. */
		while (metaStatement.hasGetter("target")) {
			Object object = metaStatement.getValue("target");
			metaStatement = MetaObject.forObject(object, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY,
					DEFAULT_REFLECTOR_FACTORY);
		}
		return metaStatement;
	}

	@Override
	public Object plugin(Object target) {
		if (target instanceof StatementHandler) {
			return Plugin.wrap(target, this);
		} else {
			return target;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setProperties(Properties properties) {
		dialectValue = properties.getProperty(DIALECT);
		if (dialectValue == null || "".equals(dialectValue)) {
			logger.error(AutoMapperExceptionEnum.DIALECT_PROPERTY_CANNOT_FOUND.description());
		} else {
			if (MYSQL.equalsIgnoreCase(dialectValue) || H2.equalsIgnoreCase(dialectValue)) {
				isMysqlOrH2 = true;
			}
			if (MYSQL.equalsIgnoreCase(dialectValue)) {
				isMysql = true;
			}
		}

		String temp = properties.getProperty(LOG_LEVEL);
		if (temp != null) {
			logLevel = LogLevel.forValue(temp);
		}
		if (logLevel == null) {
			logLevel = LogLevel.NONE;
		}

		String loggerDescriptionClassPath = properties.getProperty(LOGGER_DESCRIPTION);
		if (loggerDescriptionClassPath != null) {
			try {
				Class<? extends LoggerDescriptionable> clazz = (Class<? extends LoggerDescriptionable>) Class
						.forName(loggerDescriptionClassPath);
				loggerDescriptionHandler = clazz.newInstance();
			} catch (Exception e) {
				loggerDescriptionHandler = null;
				logger.error(new StringBuilder(AutoMapperExceptionEnum.WRONG_LOGGER_DESCRIPTION.description())
						.append(loggerDescriptionClassPath).append(" because of ").append(e).toString());
			}
		}
	}

	private SqlSource buildSqlSource(Configuration configuration, String originalSql, Class<?> parameterType) {
		if (builder == null) {
			builder = new SqlSourceBuilder(configuration);
		}
		return builder.parse(originalSql, parameterType, null);
	}

	@SuppressWarnings("unchecked")
	private void setParameters(PreparedStatement ps, MappedStatement mappedStatement, BoundSql boundSql,
			Object parameterObject) throws SQLException {
		List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
		if (parameterMappings != null) {
			Configuration configuration = mappedStatement.getConfiguration();
			TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
			MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);
			for (int i = 0; i < parameterMappings.size(); i++) {
				ParameterMapping parameterMapping = parameterMappings.get(i);
				if (parameterMapping.getMode() != ParameterMode.OUT) {
					Object value;
					String propertyName = parameterMapping.getProperty();
					PropertyTokenizer prop = new PropertyTokenizer(propertyName);
					if (parameterObject == null) {
						value = null;
					} else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
						value = parameterObject;
					} else if (boundSql.hasAdditionalParameter(propertyName)) {
						value = boundSql.getAdditionalParameter(propertyName);
					} else if (propertyName.startsWith(ForEachSqlNode.ITEM_PREFIX)
							&& boundSql.hasAdditionalParameter(prop.getName())) {
						value = boundSql.getAdditionalParameter(prop.getName());
						if (value != null) {
							value = configuration.newMetaObject(value)
									.getValue(propertyName.substring(prop.getName().length()));
						}
					} else {
						value = metaObject == null ? null : metaObject.getValue(propertyName);
					}
					TypeHandler<Object> typeHandler = (TypeHandler<Object>) parameterMapping.getTypeHandler();
					if (typeHandler == null) {
						throw new AutoMapperException(
								new StringBuilder(AutoMapperExceptionEnum.NO_TYPE_HANDLER_SUITABLE.toString())
										.append(propertyName).append(" of statement ").append(mappedStatement.getId())
										.toString());
					}
					typeHandler.setParameter(ps, i + 1, value, parameterMapping.getJdbcType());
				}
			}
		}
	}

	private String generatePageSql(String sql, Conditionable condition) {
		if ((condition != null) && (dialectValue != null) && (!dialectValue.equals(""))) {
			StringBuilder pageSql = new StringBuilder();
			if (isMysqlOrH2) {
				if (condition.getSorter() == null) {
					pageSql.append(sql);
				} else {
					if (sql.endsWith(LIMIT_1)) {
						pageSql.append(sql.substring(0, sql.length() - 8));
						pageSql.append(condition.getSorter().toSql());
						pageSql.append(LIMIT_1);
					} else {
						pageSql.append(sql);
						pageSql.append(condition.getSorter().toSql());
					}
				}
				if (condition.getLimiter() != null) {
					pageSql.append(" limit " + condition.getLimiter().getLimitFrom() + ","
							+ condition.getLimiter().getPageSize());
				}
			}
			return pageSql.toString();
		} else {
			return sql;
		}
	}

	public static boolean isMysql() {
		return isMysql;
	}
}
