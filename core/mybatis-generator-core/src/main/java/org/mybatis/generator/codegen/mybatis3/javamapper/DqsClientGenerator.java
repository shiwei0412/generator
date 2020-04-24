/**
 *    Copyright 2006-2020 the original author or authors.
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
package org.mybatis.generator.codegen.mybatis3.javamapper;

import static org.mybatis.generator.internal.util.StringUtility.stringHasValue;
import static org.mybatis.generator.internal.util.messages.Messages.getString;

import java.util.ArrayList;
import java.util.List;

import org.mybatis.generator.api.CommentGenerator;
import org.mybatis.generator.api.FullyQualifiedTable;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.Plugin;
import org.mybatis.generator.api.dom.java.CompilationUnit;
import org.mybatis.generator.api.dom.java.Field;
import org.mybatis.generator.api.dom.java.FullyQualifiedJavaType;
import org.mybatis.generator.api.dom.java.JavaVisibility;
import org.mybatis.generator.api.dom.java.Method;
import org.mybatis.generator.api.dom.java.TopLevelClass;
import org.mybatis.generator.codegen.AbstractJavaClientGenerator;
import org.mybatis.generator.codegen.AbstractXmlGenerator;
import org.mybatis.generator.config.CommentGeneratorConfiguration;
import org.mybatis.generator.config.PropertyRegistry;
import org.mybatis.generator.internal.XingtianCommentGenerator;

public class DqsClientGenerator extends AbstractJavaClientGenerator {

    public DqsClientGenerator(String project) {
        super(project,false);
    }

    @Override
    public List<CompilationUnit> getCompilationUnits() {
        FullyQualifiedTable table = introspectedTable.getFullyQualifiedTable();
        progressCallback.startTask(getString(
                "Progress.8", table.toString())); //$NON-NLS-1$
        Plugin plugins = context.getPlugins();
        //TODO shiwei 自定义实现注解的话，可以自定义commentGenerator实现，参考： https://blog.csdn.net/ieflex/article/details/81016832
        CommentGenerator commentGenerator = null;
        if(introspectedTable.getTableConfiguration().isUseXingtianExecutor()) {
        	commentGenerator = new XingtianCommentGenerator();
        }else {
        	commentGenerator = context.getCommentGenerator();
        }

        FullyQualifiedJavaType type = new FullyQualifiedJavaType(
                introspectedTable.getMyBatis3JavaMapperType());
        TopLevelClass topLevelClass = new TopLevelClass(type);
        topLevelClass.setVisibility(JavaVisibility.PUBLIC);
        
        commentGenerator.addJavaFileComment(topLevelClass);

        //添加spring注解。
        if(commentGenerator instanceof XingtianCommentGenerator) {
        	topLevelClass.addImportedType(new FullyQualifiedJavaType("org.springframework.stereotype.Service"));
        	topLevelClass.addImportedType(new FullyQualifiedJavaType("org.springframework.beans.factory.annotation.Autowired"));
        	topLevelClass.addImportedType(new FullyQualifiedJavaType("org.springframework.web.bind.annotation.*"));
        }
        
        FullyQualifiedJavaType executorfqjt = new FullyQualifiedJavaType("com.netease.mail.dp.fuxi.integration.iqmc.executor.QueryExecutor");
        
        Field field = new Field("queryExecutor", executorfqjt);
        field.setVisibility(JavaVisibility.PRIVATE);
        topLevelClass.addField(field);
        topLevelClass.addImportedType(field.getType());
        
        //TODO 从JavaMapperGenerator拷贝过来的，作用暂时未知
        String rootInterface = introspectedTable
                .getTableConfigurationProperty(PropertyRegistry.ANY_ROOT_INTERFACE);
        if (!stringHasValue(rootInterface)) {
            rootInterface = context.getJavaClientGeneratorConfiguration()
                    .getProperty(PropertyRegistry.ANY_ROOT_INTERFACE);
        }

        if (stringHasValue(rootInterface)) {
            FullyQualifiedJavaType fqjt = new FullyQualifiedJavaType(
                    rootInterface);
            topLevelClass.addSuperInterface(fqjt);
            topLevelClass.addImportedType(fqjt);
        }
        commentGenerator.addModelClassComment(topLevelClass, introspectedTable);
        
        topLevelClass.addAnnotation("@Service");

        addSelectMethod(topLevelClass);
        
        //添加带有聚合函数的
        addSelectWithAggregateMethod(topLevelClass);
        
        List<CompilationUnit> answer = new ArrayList<>();
        //TODO shiwei 这里是加载插件的地方，例如toString插件、serial插件、equals插件，在dqsclient中是不需要的
//        if (context.getPlugins().modelBaseRecordClassGenerated(topLevelClass, introspectedTable)) {
            answer.add(topLevelClass);
//        }
        return answer;
    }
   
    private void addSelectWithAggregateMethod(TopLevelClass topLevelClass) {
		// TODO Auto-generated method stub
		
	}

	protected void addSelectMethod(TopLevelClass topLevelClass) {
        Method method = new Method("selectDemo");
        method.setVisibility(JavaVisibility.PUBLIC);
        context.getCommentGenerator().addGeneralMethodComment(method, introspectedTable);
        
        topLevelClass.addImportedType(introspectedTable.getBaseRecordType());

        StringBuilder sb = new StringBuilder();
        topLevelClass.addImportedType(new FullyQualifiedJavaType("com.netease.mail.dp.fuxi.integration.iqmc.model.QueryParam"));
        sb.setLength(0);
        sb.append("QueryParam queryParam = QueryParam.create().setQueryDataType(QueryDataType.QUERY);");
        method.addBodyLine(sb.toString());
        
        List<IntrospectedColumn> primaryKeyColumns= introspectedTable.getPrimaryKeyColumns();
        if(primaryKeyColumns != null && primaryKeyColumns.size() > 0) {
        	topLevelClass.addImportedType(new FullyQualifiedJavaType("java.util.List"));
        	topLevelClass.addImportedType(new FullyQualifiedJavaType("java.util.ArrayList"));
        	topLevelClass.addImportedType(new FullyQualifiedJavaType("com.netease.mail.dp.dqs.model.order.OrderBy"));
        	sb.setLength(0);
        	sb.append("List<OrderBy> orderByFieldList = new ArrayList<OrderBy>();");
        	method.addBodyLine(sb.toString());
        	for(IntrospectedColumn primaryKey : primaryKeyColumns) {
        		sb.setLength(0);
        		sb.append("orderByFieldList.add(new OrderBy(");
        		sb.append("\"");
        		sb.append(primaryKey.getActualColumnName());
        		sb.append("\"");
        		sb.append(",false));");
        		method.addBodyLine(sb.toString());
        	}
        	sb.setLength(0);
    		sb.append("queryParam.setOrderByFields(orderByFieldList);");
    		method.addBodyLine(sb.toString());
        }
        
        FullyQualifiedJavaType fqjt = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());
        sb.setLength(0);
		sb.append("List<");
		sb.append(fqjt.getShortName());
		sb.append("> dtoList = queryExecutor.execute(");
		sb.append(fqjt.getShortName()+".class, queryParam);");
		method.addBodyLine(sb.toString());

        topLevelClass.addMethod(method);
    }

	@Override
	public AbstractXmlGenerator getMatchedXMLGenerator() {
		// TODO Auto-generated method stub
		return null;
	}
}
