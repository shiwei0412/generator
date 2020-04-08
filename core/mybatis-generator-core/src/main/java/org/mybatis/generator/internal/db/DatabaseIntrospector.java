/**
 *    Copyright 2006-2019 the original author or authors.
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
package org.mybatis.generator.internal.db;

import static org.mybatis.generator.internal.util.StringUtility.composeFullyQualifiedTableName;
import static org.mybatis.generator.internal.util.StringUtility.isTrue;
import static org.mybatis.generator.internal.util.StringUtility.stringContainsSQLWildcard;
import static org.mybatis.generator.internal.util.StringUtility.stringContainsSpace;
import static org.mybatis.generator.internal.util.StringUtility.stringHasValue;
import static org.mybatis.generator.internal.util.messages.Messages.getString;

import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mybatis.generator.api.FullyQualifiedTable;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.JavaTypeResolver;
import org.mybatis.generator.api.dom.java.FullyQualifiedJavaType;
import org.mybatis.generator.api.dom.java.JavaReservedWords;
import org.mybatis.generator.config.ColumnOverride;
import org.mybatis.generator.config.Context;
import org.mybatis.generator.config.GeneratedKey;
import org.mybatis.generator.config.PropertyRegistry;
import org.mybatis.generator.config.TableConfiguration;
import org.mybatis.generator.internal.ObjectFactory;
import org.mybatis.generator.internal.util.JavaBeansUtil;
import org.mybatis.generator.logging.Log;
import org.mybatis.generator.logging.LogFactory;

import com.alibaba.fastjson.JSON;

public class DatabaseIntrospector {

    private DatabaseMetaData databaseMetaData;

    private JavaTypeResolver javaTypeResolver;

    private List<String> warnings;

    private Context context;

    private Log logger;

    public DatabaseIntrospector(Context context,
            DatabaseMetaData databaseMetaData,
            JavaTypeResolver javaTypeResolver, List<String> warnings) {
        super();
        this.context = context;
        this.databaseMetaData = databaseMetaData;
        this.javaTypeResolver = javaTypeResolver;
        this.warnings = warnings;
        logger = LogFactory.getLog(getClass());
    }

    //获取表的主键信息。参考 JDBC元数据操作（一）-- DatabaseMetaData接口详解：https://my.oschina.net/u/1264788/blog/1627926
    private void calculatePrimaryKey(FullyQualifiedTable table,
            IntrospectedTable introspectedTable) {
        ResultSet rs = null;

        try {
            rs = databaseMetaData.getPrimaryKeys(
                    table.getIntrospectedCatalog(), table
                            .getIntrospectedSchema(), table
                            .getIntrospectedTableName());
        } catch (SQLException e) {
            closeResultSet(rs);
            warnings.add(getString("Warning.15")); //$NON-NLS-1$
            return;
        }

        try {
            // keep primary columns in key sequence order
            Map<Short, String> keyColumns = new TreeMap<>();
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME"); //$NON-NLS-1$  列名
                //序列号（当一个表有多个主键时，每个主键都有个序列号，short类型，例如test_plan_validate_result表有三个主键，
                //id、client_type、create_uid，则keyColumns的结果应该是{1=id，2=clientType，3=create_uid}，这里rs.next()迭代时并非是1、2、3的顺序
                short keySeq = rs.getShort("KEY_SEQ"); //$NON-NLS-1$  
                keyColumns.put(keySeq, columnName);
            }

            for (String columnName : keyColumns.values()) {
                introspectedTable.addPrimaryKeyColumn(columnName);
            }
        } catch (SQLException e) {
            // ignore the primary key if there's any error
        } finally {
            closeResultSet(rs);
        }
    }

    private void closeResultSet(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    private void reportIntrospectionWarnings(
            IntrospectedTable introspectedTable,
            TableConfiguration tableConfiguration, FullyQualifiedTable table) {
        // make sure that every column listed in column overrides
        // actually exists in the table
        for (ColumnOverride columnOverride : tableConfiguration
                .getColumnOverrides()) {
            if (!introspectedTable.getColumn(columnOverride.getColumnName()).isPresent()) {
                warnings.add(getString("Warning.3", //$NON-NLS-1$
                        columnOverride.getColumnName(), table.toString()));
            }
        }

        // make sure that every column listed in ignored columns
        // actually exists in the table
        for (String string : tableConfiguration.getIgnoredColumnsInError()) {
            warnings.add(getString("Warning.4", //$NON-NLS-1$
                    string, table.toString()));
        }

        GeneratedKey generatedKey = tableConfiguration.getGeneratedKey();
        if (generatedKey != null
                && !introspectedTable.getColumn(generatedKey.getColumn()).isPresent()) {
            if (generatedKey.isIdentity()) {
                warnings.add(getString("Warning.5", //$NON-NLS-1$
                        generatedKey.getColumn(), table.toString()));
            } else {
                warnings.add(getString("Warning.6", //$NON-NLS-1$
                        generatedKey.getColumn(), table.toString()));
            }
        }

        for (IntrospectedColumn ic : introspectedTable.getAllColumns()) {
            if (JavaReservedWords.containsWord(ic.getJavaProperty())) {
                warnings.add(getString("Warning.26", //$NON-NLS-1$
                        ic.getActualColumnName(), table.toString()));
            }
        }
    }

    /**
     * Returns a List of IntrospectedTable elements that matches the specified table configuration.
     *
     * @param tc
     *            the table configuration
     * @return a list of introspected tables
     * @throws SQLException
     *             if any errors in introspection
     */
    //TODO shiwei 解析数据库表的地方，入参tc是对应一个table配置
    public List<IntrospectedTable> introspectTables(TableConfiguration tc)
            throws SQLException {

        // get the raw columns from the DB
    	//解析数据库表的列
        Map<ActualTableName, List<IntrospectedColumn>> columns = getColumns(tc);

        if (columns.isEmpty()) {
            warnings.add(getString("Warning.19", tc.getCatalog(), //$NON-NLS-1$
                    tc.getSchema(), tc.getTableName()));
            return Collections.emptyList();
        }
        //移除table中配置的ignoreColumn
        removeIgnoredColumns(tc, columns);
        //根据table中的配置，对表的一些属性做处理，例如：columnRenamingRule是对列名重命名，useActualColumnNames（属性设置为true，生成的model类会直接使用column本身的名字）等
        calculateExtraColumnInformation(tc, columns);
        //应用columnOverride规则
        applyColumnOverrides(tc, columns);
        //处理主键列
        calculateIdentityColumns(tc, columns);

        //实例化table，并初始化一些属性
        List<IntrospectedTable> introspectedTables = calculateIntrospectedTables(
                tc, columns);

        // now introspectedTables has all the columns from all the
        // tables in the configuration. Do some validation...
        //对标做一下校验
        Iterator<IntrospectedTable> iter = introspectedTables.iterator();
        while (iter.hasNext()) {
            IntrospectedTable introspectedTable = iter.next();
            //表没有列的校验
            if (!introspectedTable.hasAnyColumns()) {
                // add warning that the table has no columns, remove from the
                // list
                String warning = getString(
                                "Warning.1", introspectedTable.getFullyQualifiedTable().toString()); //$NON-NLS-1$
                warnings.add(warning);
                iter.remove();
            } else if (!introspectedTable.hasPrimaryKeyColumns()
                    && !introspectedTable.hasBaseColumns()) {
            	//表没有主键列且没有base列的校验，相当于只有BOLB列时需要报WARNING，introspectedTable一共有三种列：primaryKeyColumns、baseColumns、blobColumns
                // add warning that the table has only BLOB columns, remove from
                // the list
                String warning = getString(
                                "Warning.18", introspectedTable.getFullyQualifiedTable().toString()); //$NON-NLS-1$
                warnings.add(warning);
                iter.remove();
            } else {
                // now make sure that all columns called out in the
                // configuration
                // actually exist
            	//校验配置文件中的override列、ignore列、主键列，看它们是否存在于table中
                reportIntrospectionWarnings(introspectedTable, tc,
                        introspectedTable.getFullyQualifiedTable());
            }
        }

        return introspectedTables;
    }

    private void removeIgnoredColumns(TableConfiguration tc,
            Map<ActualTableName, List<IntrospectedColumn>> columns) {
        for (Map.Entry<ActualTableName, List<IntrospectedColumn>> entry : columns
                .entrySet()) {
            Iterator<IntrospectedColumn> tableColumns = entry.getValue()
                    .iterator();
            while (tableColumns.hasNext()) {
                IntrospectedColumn introspectedColumn = tableColumns.next();
                if (tc.isColumnIgnored(introspectedColumn
                                .getActualColumnName())) {
                    tableColumns.remove();
                    if (logger.isDebugEnabled()) {
                        logger.debug(getString("Tracing.3", //$NON-NLS-1$
                                introspectedColumn.getActualColumnName(), entry
                                        .getKey().toString()));
                    }
                }
            }
        }
    }

    private void calculateExtraColumnInformation(TableConfiguration tc,
            Map<ActualTableName, List<IntrospectedColumn>> columns) {
        StringBuilder sb = new StringBuilder();
        Pattern pattern = null;
        String replaceString = null;
        
        /**
         * 该元素会在根据表中列名计算对象属性名之前先重命名列名，非常适合用于表中的列都有公用的前缀字符串的时候，
            比如列名为：CUST_ID,CUST_NAME,CUST_EMAIL,CUST_ADDRESS等；
            那么就可以设置searchString为"^CUST_"，并使用空白替换，那么生成的Customer对象中的属性名称就不是
            custId,custName等，而是先被替换为ID,NAME,EMAIL,然后变成属性：id，name，email；
            
            注意，MBG是使用java.util.regex.Matcher.replaceAll来替换searchString和replaceString的，
            如果使用了columnOverride元素，该属性无效；
         */
        if (tc.getColumnRenamingRule() != null) {
            pattern = Pattern.compile(tc.getColumnRenamingRule()
                    .getSearchString());
            replaceString = tc.getColumnRenamingRule().getReplaceString();
            replaceString = replaceString == null ? "" : replaceString; //$NON-NLS-1$
        }

        for (Map.Entry<ActualTableName, List<IntrospectedColumn>> entry : columns.entrySet()) {
            for (IntrospectedColumn introspectedColumn : entry.getValue()) {
                String calculatedColumnName;
                if (pattern == null) {
                    calculatedColumnName = introspectedColumn
                            .getActualColumnName();
                } else {
                    Matcher matcher = pattern.matcher(introspectedColumn
                            .getActualColumnName());
                    calculatedColumnName = matcher.replaceAll(replaceString);
                }

                //TODO shiwei 如果设置为true，生成的model类会直接使用column本身的名字，而不会再使用驼峰命名方法
                if (isTrue(tc.getProperty(PropertyRegistry.TABLE_USE_ACTUAL_COLUMN_NAMES))) {
                    introspectedColumn.setJavaProperty(
                            JavaBeansUtil.getValidPropertyName(calculatedColumnName));
                } else if (isTrue(tc.getProperty(PropertyRegistry.TABLE_USE_COMPOUND_PROPERTY_NAMES))) {
                    sb.setLength(0);
                    sb.append(calculatedColumnName);
                    sb.append('_');
                    sb.append(JavaBeansUtil.getCamelCaseString(
                            introspectedColumn.getRemarks(), true));
                    introspectedColumn.setJavaProperty(
                            JavaBeansUtil.getValidPropertyName(sb.toString()));
                } else {
                    introspectedColumn.setJavaProperty(
                            JavaBeansUtil.getCamelCaseString(calculatedColumnName, false));
                }

                //TODO shiwei 根据jdbcType计算java类型
                FullyQualifiedJavaType fullyQualifiedJavaType = javaTypeResolver
                        .calculateJavaType(introspectedColumn);

                if (fullyQualifiedJavaType != null) {
                    introspectedColumn
                            .setFullyQualifiedJavaType(fullyQualifiedJavaType);
                    introspectedColumn.setJdbcTypeName(javaTypeResolver
                            .calculateJdbcTypeName(introspectedColumn));
                } else {
                    // type cannot be resolved. Check for ignored or overridden
                    boolean warn = true;
                    if (tc.isColumnIgnored(introspectedColumn
                            .getActualColumnName())) {
                        warn = false;
                    }

                    ColumnOverride co = tc.getColumnOverride(introspectedColumn
                            .getActualColumnName());
                    if (co != null
                            && stringHasValue(co.getJavaType())) {
                        warn = false;
                    }

                    // if the type is not supported, then we'll report a warning
                    if (warn) {
                        introspectedColumn
                                .setFullyQualifiedJavaType(FullyQualifiedJavaType
                                        .getObjectInstance());
                        introspectedColumn.setJdbcTypeName("OTHER"); //$NON-NLS-1$

                        String warning = getString("Warning.14", //$NON-NLS-1$
                                Integer.toString(introspectedColumn.getJdbcType()),
                                entry.getKey().toString(),
                                introspectedColumn.getActualColumnName());

                        warnings.add(warning);
                    }
                }

                //!-- 自动识别数据库关键字，默认false，如果设置为true，根据SqlReservedWords中定义的关键字列表；
                if (context.autoDelimitKeywords()
                        && SqlReservedWords.containsWord(introspectedColumn
                            .getActualColumnName())) {
                    introspectedColumn.setColumnNameDelimited(true);
                }
                //delimitAllColumns：设置是否所有生成的SQL中的列名都使用标识符引起来。默认为false，delimitIdentifiers参考context的属性
                if (tc.isAllColumnDelimitingEnabled()) {
                    introspectedColumn.setColumnNameDelimited(true);
                }
            }
        }
    }

    private void calculateIdentityColumns(TableConfiguration tc,
            Map<ActualTableName, List<IntrospectedColumn>> columns) {
    	
    	/**
    	 * generatedKey用于生成生成主键的方法，
            如果设置了该元素，MBG会在生成的<insert>元素中生成一条正确的<selectKey>元素，该元素可选
            column:主键的列名；
            sqlStatement：要生成的selectKey语句，有以下可选项：
                Cloudscape:相当于selectKey的SQL为： VALUES IDENTITY_VAL_LOCAL()
                DB2       :相当于selectKey的SQL为： VALUES IDENTITY_VAL_LOCAL()
                DB2_MF    :相当于selectKey的SQL为：SELECT IDENTITY_VAL_LOCAL() FROM SYSIBM.SYSDUMMY1
                Derby      :相当于selectKey的SQL为：VALUES IDENTITY_VAL_LOCAL()
                HSQLDB      :相当于selectKey的SQL为：CALL IDENTITY()
                Informix  :相当于selectKey的SQL为：select dbinfo('sqlca.sqlerrd1') from systables where tabid=1
                MySql      :相当于selectKey的SQL为：SELECT LAST_INSERT_ID()
                SqlServer :相当于selectKey的SQL为：SELECT SCOPE_IDENTITY()
                SYBASE      :相当于selectKey的SQL为：SELECT @@IDENTITY
                JDBC      :相当于在生成的insert元素上添加useGeneratedKeys="true"和keyProperty属性
    	 */
        GeneratedKey gk = tc.getGeneratedKey();
        if (gk == null) {
            // no generated key, then no identity or sequence columns
            return;
        }

        for (Map.Entry<ActualTableName, List<IntrospectedColumn>> entry : columns
                .entrySet()) {
            for (IntrospectedColumn introspectedColumn : entry.getValue()) {
                if (isMatchedColumn(introspectedColumn, gk)) {
                    if (gk.isIdentity() || gk.isJdbcStandard()) {
                        introspectedColumn.setIdentity(true);
                        introspectedColumn.setSequenceColumn(false);
                    } else {
                        introspectedColumn.setIdentity(false);
                        introspectedColumn.setSequenceColumn(true);
                    }
                }
            }
        }
    }

    private boolean isMatchedColumn(IntrospectedColumn introspectedColumn, GeneratedKey gk) {
        if (introspectedColumn.isColumnNameDelimited()) {
            return introspectedColumn.getActualColumnName().equals(gk.getColumn());
        } else {
            return introspectedColumn.getActualColumnName().equalsIgnoreCase(gk.getColumn());
        }
    }

    /** 
     * shiwei03  用来修改表中某个列的属性，MBG会使用修改后的列来生成domain的属性；
	    column:要重新设置的列名；注意，一个table元素中可以有多个columnOverride元素哈~
     */
    private void applyColumnOverrides(TableConfiguration tc,
            Map<ActualTableName, List<IntrospectedColumn>> columns) {
        for (Map.Entry<ActualTableName, List<IntrospectedColumn>> entry : columns
                .entrySet()) {
            for (IntrospectedColumn introspectedColumn : entry.getValue()) {
                ColumnOverride columnOverride = tc
                        .getColumnOverride(introspectedColumn
                                .getActualColumnName());

                if (columnOverride != null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(getString("Tracing.4", //$NON-NLS-1$
                                introspectedColumn.getActualColumnName(), entry
                                        .getKey().toString()));
                    }

                    if (stringHasValue(columnOverride
                            .getJavaProperty())) {
                        introspectedColumn.setJavaProperty(columnOverride
                                .getJavaProperty());
                    }

                    if (stringHasValue(columnOverride
                            .getJavaType())) {
                        introspectedColumn
                                .setFullyQualifiedJavaType(new FullyQualifiedJavaType(
                                        columnOverride.getJavaType()));
                    }

                    if (stringHasValue(columnOverride
                            .getJdbcType())) {
                        introspectedColumn.setJdbcTypeName(columnOverride
                                .getJdbcType());
                    }

                    if (stringHasValue(columnOverride
                            .getTypeHandler())) {
                        introspectedColumn.setTypeHandler(columnOverride
                                .getTypeHandler());
                    }

                    if (columnOverride.isColumnNameDelimited()) {
                        introspectedColumn.setColumnNameDelimited(true);
                    }

                    introspectedColumn.setGeneratedAlways(columnOverride.isGeneratedAlways());

                    introspectedColumn.setProperties(columnOverride
                            .getProperties());

                }
            }
        }
    }

    //TODO shiwei 解析列的地方
    private Map<ActualTableName, List<IntrospectedColumn>> getColumns(
            TableConfiguration tc) throws SQLException {
        String localCatalog;
        String localSchema;
        String localTableName;

        /**
         * 注意：大小写敏感问题。正常情况下，MBG会自动的去识别数据库标识符的大小写敏感度，在一般情况下，MBG会
            根据设置的schema，catalog或tablename去查询数据表，按照下面的流程：
            1，如果schema，catalog或tablename中有空格，那么设置的是什么格式，就精确的使用指定的大小写格式去查询；
            2，否则，如果数据库的标识符使用大写的，那么MBG自动把表名变成大写再查找；
            3，否则，如果数据库的标识符使用小写的，那么MBG自动把表名变成小写再查找；
            4，否则，使用指定的大小写格式查询；
        另外的，如果在创建表的时候，使用的""把数据库对象规定大小写，就算数据库标识符是使用的大写，在这种情况下也会使用给定的大小写来创建表名；
        这个时候，请设置delimitIdentifiers="true"即可保留大小写格式；
         */
        boolean delimitIdentifiers = tc.isDelimitIdentifiers()
                || stringContainsSpace(tc.getCatalog())
                || stringContainsSpace(tc.getSchema())
                || stringContainsSpace(tc.getTableName());

        if (delimitIdentifiers) {
            localCatalog = tc.getCatalog();
            localSchema = tc.getSchema();
            localTableName = tc.getTableName();
        } else if (databaseMetaData.storesLowerCaseIdentifiers()) {
            localCatalog = tc.getCatalog() == null ? null : tc.getCatalog()
                    .toLowerCase();
            localSchema = tc.getSchema() == null ? null : tc.getSchema()
                    .toLowerCase();
            localTableName = tc.getTableName() == null ? null : tc
                    .getTableName().toLowerCase();
        } else if (databaseMetaData.storesUpperCaseIdentifiers()) {
            localCatalog = tc.getCatalog() == null ? null : tc.getCatalog()
                    .toUpperCase();
            localSchema = tc.getSchema() == null ? null : tc.getSchema()
                    .toUpperCase();
            localTableName = tc.getTableName() == null ? null : tc
                    .getTableName().toUpperCase();
        } else {
            localCatalog = tc.getCatalog();
            localSchema = tc.getSchema();
            localTableName = tc.getTableName();
        }

        // Wildcard 通配符，escaping 转义
        if (tc.isWildcardEscapingEnabled()) {
            String escapeString = databaseMetaData.getSearchStringEscape();

            StringBuilder sb = new StringBuilder();
            StringTokenizer st;
            if (localSchema != null) {
                st = new StringTokenizer(localSchema, "_%", true); //$NON-NLS-1$
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    if (token.equals("_") //$NON-NLS-1$
                            || token.equals("%")) { //$NON-NLS-1$
                        sb.append(escapeString);
                    }
                    sb.append(token);
                }
                localSchema = sb.toString();
            }

            sb.setLength(0);
            st = new StringTokenizer(localTableName, "_%", true); //$NON-NLS-1$
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                if (token.equals("_") //$NON-NLS-1$
                        || token.equals("%")) { //$NON-NLS-1$
                    sb.append(escapeString);
                }
                sb.append(token);
            }
            localTableName = sb.toString();
        }

        Map<ActualTableName, List<IntrospectedColumn>> answer = new HashMap<>();

        if (logger.isDebugEnabled()) {
            String fullTableName = composeFullyQualifiedTableName(localCatalog, localSchema,
                            localTableName, '.');
            logger.debug(getString("Tracing.1", fullTableName)); //$NON-NLS-1$
        }

        ResultSet rs = databaseMetaData.getColumns(localCatalog, localSchema,
                localTableName, "%"); //$NON-NLS-1$

        boolean supportsIsAutoIncrement = false;
        boolean supportsIsGeneratedColumn = false;
        ResultSetMetaData rsmd = rs.getMetaData();
        int colCount = rsmd.getColumnCount();
        for (int i = 1; i <= colCount; i++) {
            if ("IS_AUTOINCREMENT".equals(rsmd.getColumnName(i))) { //$NON-NLS-1$
                supportsIsAutoIncrement = true;
            }
            if ("IS_GENERATEDCOLUMN".equals(rsmd.getColumnName(i))) { //$NON-NLS-1$
                supportsIsGeneratedColumn = true;
            }
        }

        while (rs.next()) {
            IntrospectedColumn introspectedColumn = ObjectFactory
                    .createIntrospectedColumn(context);

            introspectedColumn.setTableAlias(tc.getAlias());
            introspectedColumn.setJdbcType(rs.getInt("DATA_TYPE")); //$NON-NLS-1$
            introspectedColumn.setActualTypeName(rs.getString("TYPE_NAME")); //$NON-NLS-1$
            introspectedColumn.setLength(rs.getInt("COLUMN_SIZE")); //$NON-NLS-1$
            introspectedColumn.setActualColumnName(rs.getString("COLUMN_NAME")); //$NON-NLS-1$
            introspectedColumn
                    .setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable); //$NON-NLS-1$
            introspectedColumn.setScale(rs.getInt("DECIMAL_DIGITS")); //$NON-NLS-1$
            introspectedColumn.setRemarks(rs.getString("REMARKS")); //$NON-NLS-1$
            introspectedColumn.setDefaultValue(rs.getString("COLUMN_DEF")); //$NON-NLS-1$

            if (supportsIsAutoIncrement) {
                introspectedColumn.setAutoIncrement(
                        "YES".equals(rs.getString("IS_AUTOINCREMENT"))); //$NON-NLS-1$ //$NON-NLS-2$
            }

            if (supportsIsGeneratedColumn) {
                introspectedColumn.setGeneratedColumn(
                        "YES".equals(rs.getString("IS_GENERATEDCOLUMN"))); //$NON-NLS-1$ //$NON-NLS-2$
            }

            ActualTableName atn = new ActualTableName(
                    rs.getString("TABLE_CAT"), //$NON-NLS-1$
                    rs.getString("TABLE_SCHEM"), //$NON-NLS-1$
                    rs.getString("TABLE_NAME")); //$NON-NLS-1$

            List<IntrospectedColumn> columns = answer.get(atn);
            if (columns == null) {
                columns = new ArrayList<>();
                answer.put(atn, columns);
            }
            System.out.println("shiwei03 introspectedColumn: " + introspectedColumn.toString());
            columns.add(introspectedColumn);

            if (logger.isDebugEnabled()) {
                logger.debug(getString(
                        "Tracing.2", //$NON-NLS-1$
                        introspectedColumn.getActualColumnName(), Integer
                                .toString(introspectedColumn.getJdbcType()),
                        atn.toString()));
            }
        }

        closeResultSet(rs);

        if (answer.size() > 1
                && !stringContainsSQLWildcard(localSchema)
                && !stringContainsSQLWildcard(localTableName)) {
            // issue a warning if there is more than one table and
            // no wildcards were used
            ActualTableName inputAtn = new ActualTableName(tc.getCatalog(), tc
                    .getSchema(), tc.getTableName());

            StringBuilder sb = new StringBuilder();
            boolean comma = false;
            for (ActualTableName atn : answer.keySet()) {
                if (comma) {
                    sb.append(',');
                } else {
                    comma = true;
                }
                sb.append(atn.toString());
            }

            warnings.add(getString("Warning.25", //$NON-NLS-1$
                    inputAtn.toString(), sb.toString()));
        }

        return answer;
    }
    
    
  //TODO shiwei mock 解析列的数据
    private Map<ActualTableName, List<IntrospectedColumn>> getColumnsMock(
            TableConfiguration tc) throws SQLException {
        String localCatalog;
        String localSchema;
        String localTableName;

        /**
         * 注意：大小写敏感问题。正常情况下，MBG会自动的去识别数据库标识符的大小写敏感度，在一般情况下，MBG会
            根据设置的schema，catalog或tablename去查询数据表，按照下面的流程：
            1，如果schema，catalog或tablename中有空格，那么设置的是什么格式，就精确的使用指定的大小写格式去查询；
            2，否则，如果数据库的标识符使用大写的，那么MBG自动把表名变成大写再查找；
            3，否则，如果数据库的标识符使用小写的，那么MBG自动把表名变成小写再查找；
            4，否则，使用指定的大小写格式查询；
        另外的，如果在创建表的时候，使用的""把数据库对象规定大小写，就算数据库标识符是使用的大写，在这种情况下也会使用给定的大小写来创建表名；
        这个时候，请设置delimitIdentifiers="true"即可保留大小写格式；
         */
        boolean delimitIdentifiers = tc.isDelimitIdentifiers()
                || stringContainsSpace(tc.getCatalog())
                || stringContainsSpace(tc.getSchema())
                || stringContainsSpace(tc.getTableName());

        if (delimitIdentifiers) {
            localCatalog = tc.getCatalog();
            localSchema = tc.getSchema();
            localTableName = tc.getTableName();
        } else if (databaseMetaData.storesLowerCaseIdentifiers()) {
            localCatalog = tc.getCatalog() == null ? null : tc.getCatalog()
                    .toLowerCase();
            localSchema = tc.getSchema() == null ? null : tc.getSchema()
                    .toLowerCase();
            localTableName = tc.getTableName() == null ? null : tc
                    .getTableName().toLowerCase();
        } else if (databaseMetaData.storesUpperCaseIdentifiers()) {
            localCatalog = tc.getCatalog() == null ? null : tc.getCatalog()
                    .toUpperCase();
            localSchema = tc.getSchema() == null ? null : tc.getSchema()
                    .toUpperCase();
            localTableName = tc.getTableName() == null ? null : tc
                    .getTableName().toUpperCase();
        } else {
            localCatalog = tc.getCatalog();
            localSchema = tc.getSchema();
            localTableName = tc.getTableName();
        }

        // Wildcard 通配符，escaping 转义
        if (tc.isWildcardEscapingEnabled()) {
            String escapeString = databaseMetaData.getSearchStringEscape();

            StringBuilder sb = new StringBuilder();
            StringTokenizer st;
            if (localSchema != null) {
                st = new StringTokenizer(localSchema, "_%", true); //$NON-NLS-1$
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    if (token.equals("_") //$NON-NLS-1$
                            || token.equals("%")) { //$NON-NLS-1$
                        sb.append(escapeString);
                    }
                    sb.append(token);
                }
                localSchema = sb.toString();
            }

            sb.setLength(0);
            st = new StringTokenizer(localTableName, "_%", true); //$NON-NLS-1$
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                if (token.equals("_") //$NON-NLS-1$
                        || token.equals("%")) { //$NON-NLS-1$
                    sb.append(escapeString);
                }
                sb.append(token);
            }
            localTableName = sb.toString();
        }

        Map<ActualTableName, List<IntrospectedColumn>> answer = new HashMap<>();

        if (logger.isDebugEnabled()) {
            String fullTableName = composeFullyQualifiedTableName(localCatalog, localSchema,
                            localTableName, '.');
            logger.debug(getString("Tracing.1", fullTableName)); //$NON-NLS-1$
        }

        ResultSet rs = databaseMetaData.getColumns(localCatalog, localSchema,
                localTableName, "%"); //$NON-NLS-1$

        boolean supportsIsAutoIncrement = false;
        boolean supportsIsGeneratedColumn = false;
        ResultSetMetaData rsmd = rs.getMetaData();
        int colCount = rsmd.getColumnCount();
        for (int i = 1; i <= colCount; i++) {
            if ("IS_AUTOINCREMENT".equals(rsmd.getColumnName(i))) { //$NON-NLS-1$
                supportsIsAutoIncrement = true;
            }
            if ("IS_GENERATEDCOLUMN".equals(rsmd.getColumnName(i))) { //$NON-NLS-1$
                supportsIsGeneratedColumn = true;
            }
        }

        for(int i=0;i<10;i++) {
        	IntrospectedColumn introspectedColumn = ObjectFactory.createIntrospectedColumn(context);
        	introspectedColumn.setTableAlias(tc.getAlias());
        	introspectedColumn.setJdbcType(JDBCType.BIGINT.getVendorTypeNumber()); //$NON-NLS-1$
        	introspectedColumn.setActualTypeName(rs.getString("TYPE_NAME")); //$NON-NLS-1$
        	introspectedColumn.setLength(100); //$NON-NLS-1$
        	introspectedColumn.setActualColumnName(rs.getString("COLUMN_NAME")); //$NON-NLS-1$
        	introspectedColumn
        	.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable); //$NON-NLS-1$
        	introspectedColumn.setScale(rs.getInt("DECIMAL_DIGITS")); //$NON-NLS-1$
        	introspectedColumn.setRemarks(rs.getString("REMARKS")); //$NON-NLS-1$
        	introspectedColumn.setDefaultValue(rs.getString("COLUMN_DEF")); //$NON-NLS-1$
        	
        	if (supportsIsAutoIncrement) {
        		introspectedColumn.setAutoIncrement(
        				"YES".equals(rs.getString("IS_AUTOINCREMENT"))); //$NON-NLS-1$ //$NON-NLS-2$
        	}
        	
        	if (supportsIsGeneratedColumn) {
        		introspectedColumn.setGeneratedColumn(
        				"YES".equals(rs.getString("IS_GENERATEDCOLUMN"))); //$NON-NLS-1$ //$NON-NLS-2$
        	}
        	
        	ActualTableName atn = new ActualTableName(
        			rs.getString("TABLE_CAT"), //$NON-NLS-1$
        			rs.getString("TABLE_SCHEM"), //$NON-NLS-1$
        			rs.getString("TABLE_NAME")); //$NON-NLS-1$
        	
        	List<IntrospectedColumn> columns = answer.get(atn);
        	if (columns == null) {
        		columns = new ArrayList<>();
        		answer.put(atn, columns);
        	}
        	columns.add(introspectedColumn);
        }
        


        closeResultSet(rs);

        if (answer.size() > 1
                && !stringContainsSQLWildcard(localSchema)
                && !stringContainsSQLWildcard(localTableName)) {
            // issue a warning if there is more than one table and
            // no wildcards were used
            ActualTableName inputAtn = new ActualTableName(tc.getCatalog(), tc
                    .getSchema(), tc.getTableName());

            StringBuilder sb = new StringBuilder();
            boolean comma = false;
            for (ActualTableName atn : answer.keySet()) {
                if (comma) {
                    sb.append(',');
                } else {
                    comma = true;
                }
                sb.append(atn.toString());
            }

            warnings.add(getString("Warning.25", //$NON-NLS-1$
                    inputAtn.toString(), sb.toString()));
        }

        return answer;
    }

    private List<IntrospectedTable> calculateIntrospectedTables(
            TableConfiguration tc,
            Map<ActualTableName, List<IntrospectedColumn>> columns) {
        boolean delimitIdentifiers = tc.isDelimitIdentifiers()
                || stringContainsSpace(tc.getCatalog())
                || stringContainsSpace(tc.getSchema())
                || stringContainsSpace(tc.getTableName());

        List<IntrospectedTable> answer = new ArrayList<>();

        for (Map.Entry<ActualTableName, List<IntrospectedColumn>> entry : columns
                .entrySet()) {
            ActualTableName atn = entry.getKey();

            // we only use the returned catalog and schema if something was
            // actually
            // specified on the table configuration. If something was returned
            // from the DB for these fields, but nothing was specified on the
            // table
            // configuration, then some sort of DB default is being returned
            // and we don't want that in our SQL
            FullyQualifiedTable table = new FullyQualifiedTable(
                    stringHasValue(tc.getCatalog()) ? atn.getCatalog() : null,
                    stringHasValue(tc.getSchema()) ? atn.getSchema() : null,
                    atn.getTableName(),
                    tc.getDomainObjectName(),
                    tc.getAlias(),
                    isTrue(tc.getProperty(PropertyRegistry.TABLE_IGNORE_QUALIFIERS_AT_RUNTIME)),
                    tc.getProperty(PropertyRegistry.TABLE_RUNTIME_CATALOG),
                    tc.getProperty(PropertyRegistry.TABLE_RUNTIME_SCHEMA),
                    tc.getProperty(PropertyRegistry.TABLE_RUNTIME_TABLE_NAME),
                    delimitIdentifiers,
                    tc.getDomainObjectRenamingRule(),
                    context);

            //根据context的 targetRuntime选项，实例化一个table的实现，大多时候都是IntrospectedTableMyBatis3Impl对象
            IntrospectedTable introspectedTable = ObjectFactory.createIntrospectedTable(tc, table, context);

            for (IntrospectedColumn introspectedColumn : entry.getValue()) {
                introspectedTable.addColumn(introspectedColumn);
            }

            //获取表的主键信息
            calculatePrimaryKey(table, introspectedTable);
            //获取表的一些其他信息
            enhanceIntrospectedTable(introspectedTable);

            answer.add(introspectedTable);
        }

        return answer;
    }

    /**
     * Calls database metadata to retrieve extra information about the table
     * such as remarks associated with the table and the type.
     *
     * <p>If there is any error, we just add a warning and continue.
     *
     * @param introspectedTable the introspected table to enhance
     * 
     * 获取表的一些其他信息
     */
    private void enhanceIntrospectedTable(IntrospectedTable introspectedTable) {
        try {
            FullyQualifiedTable fqt = introspectedTable.getFullyQualifiedTable();

            ResultSet rs = databaseMetaData.getTables(fqt.getIntrospectedCatalog(), fqt.getIntrospectedSchema(),
                    fqt.getIntrospectedTableName(), null);
            if (rs.next()) {
            	//REMARKS-表备注
                String remarks = rs.getString("REMARKS"); //$NON-NLS-1$
                //TABLE_TYPE-表类型，,典型的类型是 "TABLE"、"VIEW"、"SYSTEM TABLE"、"GLOBAL TEMPORARY"、"LOCAL TEMPORARY"、"ALIAS" 和 "SYNONYM"。  
                String tableType = rs.getString("TABLE_TYPE"); //$NON-NLS-1$
                introspectedTable.setRemarks(remarks);
                introspectedTable.setTableType(tableType);
            }
            closeResultSet(rs);
        } catch (SQLException e) {
            warnings.add(getString("Warning.27", e.getMessage())); //$NON-NLS-1$
        }
    }
}
