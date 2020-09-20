/*
 * Copyright (c) 2011-2020, baomidou (jobob@qq.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.baomidou.mybatisplus.generator.config.builder;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.generator.InjectionConfig;
import com.baomidou.mybatisplus.generator.config.*;
import com.baomidou.mybatisplus.generator.config.po.TableField;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.baomidou.mybatisplus.generator.config.querys.DecoratorDbQuery;
import com.baomidou.mybatisplus.generator.config.querys.H2Query;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 配置汇总 传递给文件生成工具
 *
 * @author YangHu, tangguo, hubin, Juzi
 * @since 2016-08-30
 */
@Data
@Accessors(chain = true)
public class ConfigBuilder {

    /**
     * 模板路径配置信息
     */
    @Setter(value = AccessLevel.NONE)
    private final TemplateConfig template;
    /**
     * 数据库配置
     */
    @Getter(value = AccessLevel.NONE)
    @Setter(value = AccessLevel.NONE)
    private final DataSourceConfig dataSourceConfig;
    /**
     * SQL连接
     */
    @Getter(value = AccessLevel.NONE)
    @Setter(value = AccessLevel.NONE)
    private final Connection connection;
    /**
     * 数据库表信息
     */
    private final List<TableInfo> tableInfoList = new ArrayList<>();
    /**
     * 包配置详情
     */
    @Setter(value = AccessLevel.NONE)
    private Map<String, String> packageInfo;
    /**
     * 路径配置信息
     */
    @Setter(value = AccessLevel.NONE)
    private Map<String, String> pathInfo;
    /**
     * 策略配置
     */
    private StrategyConfig strategyConfig;
    /**
     * 全局配置信息
     */
    private GlobalConfig globalConfig;
    /**
     * 注入配置信息
     */
    private InjectionConfig injectionConfig;
    /**
     * 过滤正则
     */
    private static final Pattern REGX = Pattern.compile("[~!/@#$%^&*()-_=+\\\\|[{}];:'\",<.>?]+");
    /**
     * 表数据查询
     */
    private final DecoratorDbQuery dbQuery;
    /**
     * 在构造器中处理配置
     *
     * @param packageConfig    包配置
     * @param dataSourceConfig 数据源配置
     * @param strategyConfig   表配置
     * @param template         模板配置
     * @param globalConfig     全局配置
     */
    public ConfigBuilder(PackageConfig packageConfig, DataSourceConfig dataSourceConfig, StrategyConfig strategyConfig,
                         TemplateConfig template, GlobalConfig globalConfig) {
        this.connection = dataSourceConfig.getConn();
        this.dataSourceConfig = dataSourceConfig;
        this.dbQuery = (DecoratorDbQuery) dataSourceConfig.getDbQuery();
        // 全局配置
        this.globalConfig = Optional.ofNullable(globalConfig).orElseGet(GlobalConfig::new);
        // 模板配置
        this.template = Optional.ofNullable(template).orElseGet(TemplateConfig::new);
        handlerPackage(this.template, this.globalConfig.getOutputDir(), packageConfig == null ? new PackageConfig() : packageConfig);
        // 策略配置
        this.strategyConfig = Optional.ofNullable(strategyConfig).orElseGet(StrategyConfig::new);
        this.tableInfoList.addAll(getTablesInfo());
    }

    /**
     * 处理包配置
     *
     * @param template  TemplateConfig
     * @param outputDir
     * @param config    PackageConfig
     */
    private void handlerPackage(TemplateConfig template, String outputDir, PackageConfig config) {
        // 包信息
        packageInfo = CollectionUtils.newHashMapWithExpectedSize(7);
        packageInfo.put(ConstVal.MODULE_NAME, config.getModuleName());
        packageInfo.put(ConstVal.ENTITY, config.joinPackage(config.getEntity()));
        packageInfo.put(ConstVal.MAPPER, config.joinPackage(config.getMapper()));
        packageInfo.put(ConstVal.XML, config.joinPackage(config.getXml()));
        packageInfo.put(ConstVal.SERVICE, config.joinPackage(config.getService()));
        packageInfo.put(ConstVal.SERVICE_IMPL, config.joinPackage(config.getServiceImpl()));
        packageInfo.put(ConstVal.CONTROLLER, config.joinPackage(config.getController()));

        // 自定义路径
        Map<String, String> configPathInfo = config.getPathInfo();
        if (null != configPathInfo) {
            pathInfo = configPathInfo;
        } else {
            // 生成路径信息
            pathInfo = CollectionUtils.newHashMapWithExpectedSize(6);
            setPathInfo(pathInfo, template.getEntity(getGlobalConfig().isKotlin()), outputDir, ConstVal.ENTITY_PATH, ConstVal.ENTITY);
            setPathInfo(pathInfo, template.getMapper(), outputDir, ConstVal.MAPPER_PATH, ConstVal.MAPPER);
            setPathInfo(pathInfo, template.getXml(), outputDir, ConstVal.XML_PATH, ConstVal.XML);
            setPathInfo(pathInfo, template.getService(), outputDir, ConstVal.SERVICE_PATH, ConstVal.SERVICE);
            setPathInfo(pathInfo, template.getServiceImpl(), outputDir, ConstVal.SERVICE_IMPL_PATH, ConstVal.SERVICE_IMPL);
            setPathInfo(pathInfo, template.getController(), outputDir, ConstVal.CONTROLLER_PATH, ConstVal.CONTROLLER);
        }
    }

    private void setPathInfo(Map<String, String> pathInfo, String template, String outputDir, String path, String module) {
        if (StringUtils.isNotBlank(template)) {
            pathInfo.put(path, joinPath(outputDir, packageInfo.get(module)));
        }
    }

    /**
     * 处理表对应的类名称
     */
    private void processTable() {
        for (TableInfo tableInfo : tableInfoList) {
            String entityName = strategyConfig.getNameConvert().entityNameConvert(tableInfo);
            if (StringUtils.isNotBlank(globalConfig.getEntityName())) {
                tableInfo.setConvert(true);
                tableInfo.setEntityName(String.format(globalConfig.getEntityName(), entityName));
            } else {
                tableInfo.setEntityName(strategyConfig, entityName);
            }
            if (StringUtils.isNotBlank(globalConfig.getMapperName())) {
                tableInfo.setMapperName(String.format(globalConfig.getMapperName(), entityName));
            } else {
                tableInfo.setMapperName(entityName + ConstVal.MAPPER);
            }
            if (StringUtils.isNotBlank(globalConfig.getXmlName())) {
                tableInfo.setXmlName(String.format(globalConfig.getXmlName(), entityName));
            } else {
                tableInfo.setXmlName(entityName + ConstVal.MAPPER);
            }
            if (StringUtils.isNotBlank(globalConfig.getServiceName())) {
                tableInfo.setServiceName(String.format(globalConfig.getServiceName(), entityName));
            } else {
                tableInfo.setServiceName("I" + entityName + ConstVal.SERVICE);
            }
            if (StringUtils.isNotBlank(globalConfig.getServiceImplName())) {
                tableInfo.setServiceImplName(String.format(globalConfig.getServiceImplName(), entityName));
            } else {
                tableInfo.setServiceImplName(entityName + ConstVal.SERVICE_IMPL);
            }
            if (StringUtils.isNotBlank(globalConfig.getControllerName())) {
                tableInfo.setControllerName(String.format(globalConfig.getControllerName(), entityName));
            } else {
                tableInfo.setControllerName(entityName + ConstVal.CONTROLLER);
            }
            // 检测导入包
            checkImportPackages(tableInfo);
        }
    }

    /**
     * 检测导入包
     *
     * @param tableInfo ignore
     */
    private void checkImportPackages(TableInfo tableInfo) {
        if (StringUtils.isNotBlank(strategyConfig.getSuperEntityClass())) {
            // 自定义父类
            tableInfo.getImportPackages().add(strategyConfig.getSuperEntityClass());
        } else if (globalConfig.isActiveRecord()) {
            // 无父类开启 AR 模式
            tableInfo.getImportPackages().add(com.baomidou.mybatisplus.extension.activerecord.Model.class.getCanonicalName());
        }
        if (null != globalConfig.getIdType() && tableInfo.isHavePrimaryKey()) {
            // 指定需要 IdType 场景
            tableInfo.getImportPackages().add(com.baomidou.mybatisplus.annotation.IdType.class.getCanonicalName());
            tableInfo.getImportPackages().add(com.baomidou.mybatisplus.annotation.TableId.class.getCanonicalName());
        }
        if (StringUtils.isNotBlank(strategyConfig.getVersionFieldName())
            && CollectionUtils.isNotEmpty(tableInfo.getFields())) {
            tableInfo.getFields().forEach(f -> {
                if (strategyConfig.getVersionFieldName().equals(f.getName())) {
                    tableInfo.getImportPackages().add(com.baomidou.mybatisplus.annotation.Version.class.getCanonicalName());
                }
            });
        }
    }


    /**
     * 获取所有的数据库表信息
     */
    private List<TableInfo> getTablesInfo() {
        boolean isInclude = strategyConfig.getInclude().size() > 0;
        boolean isExclude = strategyConfig.getExclude().size() > 0;
        if (isInclude && isExclude) {
            throw new RuntimeException("<strategy> 标签中 <include> 与 <exclude> 只能配置一项！");
        }
        if (strategyConfig.getNotLikeTable() != null && strategyConfig.getLikeTable() != null) {
            throw new RuntimeException("<strategy> 标签中 <likeTable> 与 <notLikeTable> 只能配置一项！");
        }
        //所有的表信息
        List<TableInfo> tableList = new ArrayList<>();

        //需要反向生成或排除的表信息
        List<TableInfo> includeTableList = new ArrayList<>();
        List<TableInfo> excludeTableList = new ArrayList<>();

        //不存在的表名
        Set<String> notExistTables = new HashSet<>();
        try {
            String tablesSql = dbQuery.tablesSql();
            StringBuilder sql = new StringBuilder(tablesSql);
            if (strategyConfig.isEnableSqlFilter()) {
                if (strategyConfig.getLikeTable() != null) {
                    sql.append(" AND ").append(dbQuery.tableName()).append(" LIKE '").append(strategyConfig.getLikeTable().getValue()).append("'");
                } else if (strategyConfig.getNotLikeTable() != null) {
                    sql.append(" AND ").append(dbQuery.tableName()).append(" NOT LIKE '").append(strategyConfig.getNotLikeTable().getValue()).append("'");
                }
                if (isInclude) {
                    sql.append(" AND ").append(dbQuery.tableName()).append(" IN (")
                        .append(strategyConfig.getInclude().stream().map(tb -> "'" + tb + "'").collect(Collectors.joining(","))).append(")");
                } else if (isExclude) {
                    sql.append(" AND ").append(dbQuery.tableName()).append(" NOT IN (")
                        .append(strategyConfig.getExclude().stream().map(tb -> "'" + tb + "'").collect(Collectors.joining(","))).append(")");
                }
            }
            TableInfo tableInfo;
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql.toString());
                 ResultSet results = preparedStatement.executeQuery()) {
                while (results.next()) {
                    final String tableName = results.getString(dbQuery.tableName());
                    if (StringUtils.isBlank(tableName)) {
                        System.err.println("当前数据库为空！！！");
                        continue;
                    }
                    tableInfo = new TableInfo();
                    tableInfo.setName(tableName);
                    String tableComment = dbQuery.getTableComment(results);
                    if (strategyConfig.isSkipView() && "VIEW".equals(tableComment)) {
                        // 跳过视图
                        continue;
                    }
                    tableInfo.setComment(tableComment);
                    if (isInclude) {
                        for (String includeTable : strategyConfig.getInclude()) {
                            // 忽略大小写等于 或 正则 true
                            if (tableNameMatches(includeTable, tableName)) {
                                includeTableList.add(tableInfo);
                            } else {
                                //过滤正则表名
                                if (!REGX.matcher(includeTable).find()) {
                                    notExistTables.add(includeTable);
                                }
                            }
                        }
                    } else if (isExclude) {
                        for (String excludeTable : strategyConfig.getExclude()) {
                            // 忽略大小写等于 或 正则 true
                            if (tableNameMatches(excludeTable, tableName)) {
                                excludeTableList.add(tableInfo);
                            } else {
                                //过滤正则表名
                                if (!REGX.matcher(excludeTable).find()) {
                                    notExistTables.add(excludeTable);
                                }
                            }
                        }
                    }
                    tableList.add(tableInfo);
                }
            }
            // 将已经存在的表移除，获取配置中数据库不存在的表
            for (TableInfo tabInfo : tableList) {
                notExistTables.remove(tabInfo.getName());
            }
            if (notExistTables.size() > 0) {
                System.err.println("表 " + notExistTables + " 在数据库中不存在！！！");
            }

            // 需要反向生成的表信息
            if (isExclude) {
                tableList.removeAll(excludeTableList);
                includeTableList = tableList;
            }
            if (!isInclude && !isExclude) {
                includeTableList = tableList;
            }
            // 性能优化，只处理需执行表字段 github issues/219
            includeTableList.forEach(this::convertTableFields);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        processTable();
        return tableList;
    }


    /**
     * 表名匹配
     *
     * @param setTableName 设置表名
     * @param dbTableName  数据库表单
     * @return ignore
     */
    private boolean tableNameMatches(String setTableName, String dbTableName) {
        return setTableName.equalsIgnoreCase(dbTableName)
            || StringUtils.matches(setTableName, dbTableName);
    }

    /**
     * 将字段信息与表信息关联
     *
     * @param tableInfo 表信息
     * @return ignore
     */
    private TableInfo convertTableFields(TableInfo tableInfo) {
        List<TableField> fieldList = new ArrayList<>();
        List<TableField> commonFieldList = new ArrayList<>();
        DbType dbType = this.dataSourceConfig.getDbType();
        String tableName = tableInfo.getName();
        try {
            String tableFieldsSql = dbQuery.tableFieldsSql(tableName);
            Set<String> h2PkColumns = new HashSet<>();
            if (DbType.H2 == dbType) {
                try (PreparedStatement pkQueryStmt = connection.prepareStatement(String.format(H2Query.PK_QUERY_SQL, tableName));
                     ResultSet pkResults = pkQueryStmt.executeQuery()) {
                    while (pkResults.next()) {
                        String primaryKey = pkResults.getString(dbQuery.fieldKey());
                        if (Boolean.parseBoolean(primaryKey)) {
                            h2PkColumns.add(pkResults.getString(dbQuery.fieldName()));
                        }
                    }
                }
            }
            try (
                PreparedStatement preparedStatement = connection.prepareStatement(tableFieldsSql);
                ResultSet results = preparedStatement.executeQuery()) {
                while (results.next()) {
                    TableField field = new TableField();
                    String columnName = results.getString(dbQuery.fieldName());
                    // 避免多重主键设置，目前只取第一个找到ID，并放到list中的索引为0的位置
                    boolean isId;
                    if (DbType.H2 == dbType) {
                        isId = h2PkColumns.contains(columnName);
                    } else {
                        String key = results.getString(dbQuery.fieldKey());
                        if (DbType.DB2 == dbType || DbType.SQLITE == dbType) {
                            isId = StringUtils.isNotBlank(key) && "1".equals(key);
                        } else {
                            isId = StringUtils.isNotBlank(key) && "PRI".equals(key.toUpperCase());
                        }
                    }

                    // 处理ID
                    if (isId) {
                        field.setKeyFlag(true);
                        tableInfo.setHavePrimaryKey(true);
                        field.setKeyIdentityFlag(dbQuery.isKeyIdentity(results));
                    } else {
                        field.setKeyFlag(false);
                    }
                    // 自定义字段查询
                    String[] fcs = dbQuery.fieldCustom();
                    if (null != fcs) {
                        Map<String, Object> customMap = CollectionUtils.newHashMapWithExpectedSize(fcs.length);
                        for (String fc : fcs) {
                            customMap.put(fc, results.getObject(fc));
                        }
                        field.setCustomMap(customMap);
                    }
                    // 处理其它信息
                    field.setName(columnName);
                    String newColumnName = columnName;
                    IKeyWordsHandler keyWordsHandler = dataSourceConfig.getKeyWordsHandler();
                    if (keyWordsHandler != null && keyWordsHandler.isKeyWords(columnName)) {
                        System.err.printf("当前表[%s]存在字段[%s]为数据库关键字或保留字!%n", tableName, columnName);
                        field.setKeyWords(true);
                        newColumnName = keyWordsHandler.formatColumn(columnName);
                    }
                    field.setColumnName(newColumnName);
                    field.setType(results.getString(dbQuery.fieldType()));
                    field.setPropertyName(strategyConfig.getNameConvert().propertyNameConvert(field));
                    field.setColumnType(dataSourceConfig.getTypeConvert().processTypeConvert(globalConfig, field));
                    field.setComment(dbQuery.getFiledComment(results));
                    // 填充逻辑判断
                    getStrategyConfig().getTableFillList()
                        .stream()
                        //忽略大写字段问题
                        .filter(tf -> tf.getFieldName().equalsIgnoreCase(field.getName()))
                        .findFirst().ifPresent(tf -> field.setFill(tf.getFieldFill().name()));
                    if (strategyConfig.includeSuperEntityColumns(field.getName())) {
                        // 跳过公共字段
                        commonFieldList.add(field);
                        continue;
                    }
                    fieldList.add(field);
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception：" + e.getMessage());
        }
        tableInfo.setFields(fieldList);
        tableInfo.setCommonFields(commonFieldList);
        return tableInfo;
    }


    /**
     * 连接路径字符串
     *
     * @param parentDir   路径常量字符串
     * @param packageName 包名
     * @return 连接后的路径
     */
    private String joinPath(String parentDir, String packageName) {
        if (StringUtils.isBlank(parentDir)) {
            parentDir = System.getProperty(ConstVal.JAVA_TMPDIR);
        }
        if (!StringUtils.endsWith(parentDir, File.separator)) {
            parentDir += File.separator;
        }
        packageName = packageName.replaceAll("\\.", StringPool.BACK_SLASH + File.separator);
        return parentDir + packageName;
    }

    /**
     * 格式化数据库注释内容
     *
     * @param comment 注释
     * @return 注释
     * @see DecoratorDbQuery#formatComment(java.lang.String)
     * @since 3.4.0
     * @deprecated 3.4.1
     */
    public String formatComment(String comment) {
        return dbQuery.formatComment(comment);
    }

    public void close() {
        //暂时只有数据库连接需要关闭
        Optional.ofNullable(connection).ifPresent((con) -> {
            try {
                con.close();
            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
            }
        });
    }


    /**
     * 不再建议调用此方法，后续不再公开此方法.
     *
     * @param tableInfoList tableInfoList
     * @return configBuild
     * @deprecated 3.4.1 {@link #getTableInfoList()} 返回引用，如果有需要请直接操作
     */
    @Deprecated
    public ConfigBuilder setTableInfoList(List<TableInfo> tableInfoList) {
        this.tableInfoList.addAll(tableInfoList);
        return this;
    }
}
