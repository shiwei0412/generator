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
package org.mybatis.generator.codegen.mybatis3.model;

import static org.mybatis.generator.internal.util.JavaBeansUtil.getJavaBeansField;
import static org.mybatis.generator.internal.util.JavaBeansUtil.getJavaBeansGetter;
import static org.mybatis.generator.internal.util.JavaBeansUtil.getJavaBeansSetter;
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
import org.mybatis.generator.api.dom.java.Parameter;
import org.mybatis.generator.api.dom.java.TopLevelClass;
import org.mybatis.generator.codegen.AbstractJavaGenerator;
import org.mybatis.generator.codegen.RootClassInfo;

public class PrimaryKeyGenerator extends AbstractJavaGenerator {

    public PrimaryKeyGenerator(String project) {
        super(project);
    }

    @Override
    public List<CompilationUnit> getCompilationUnits() {
        FullyQualifiedTable table = introspectedTable.getFullyQualifiedTable();
        progressCallback.startTask(getString(
                "Progress.7", table.toString())); //$NON-NLS-1$
        //关于plugin部分的内容，参考1：https://blog.csdn.net/u011781521/article/details/78695396
        //参考2：http://mybatis.org/generator/reference/pluggingIn.html#
        Plugin plugins = context.getPlugins();
        CommentGenerator commentGenerator = context.getCommentGenerator();

        TopLevelClass topLevelClass = new TopLevelClass(introspectedTable
                .getPrimaryKeyType());
        topLevelClass.setVisibility(JavaVisibility.PUBLIC);
        commentGenerator.addJavaFileComment(topLevelClass);

        String rootClass = getRootClass();
        if (rootClass != null) {
            FullyQualifiedJavaType rootType = new FullyQualifiedJavaType(rootClass);
            topLevelClass.setSuperClass(rootType);
            topLevelClass.addImportedType(rootType);
        }

        if (introspectedTable.isConstructorBased()) {
            addParameterizedConstructor(topLevelClass);

            if (!introspectedTable.isImmutable()) {
                addDefaultConstructor(topLevelClass);
            }
        }

        commentGenerator.addModelClassComment(topLevelClass, introspectedTable);

        for (IntrospectedColumn introspectedColumn : introspectedTable
                .getPrimaryKeyColumns()) {
            if (RootClassInfo.getInstance(rootClass, warnings)
                    .containsProperty(introspectedColumn)) {
                continue;
            }
            //Field的注解是在getJavaBeansField()方法中添加的
            Field field = getJavaBeansField(introspectedColumn, context, introspectedTable);
            
            //plugins是PluginAggregator的实例，PluginAggregator继承自CompositePlugin，所以这里调用的是CompositePlugin的modelFieldGenerated()方法。
            //如果plugins中有任何一个返回false，那么对应生成的代码片段（JAVA或者XML）就不会被生成了
            if (plugins.modelFieldGenerated(field, topLevelClass,
                    introspectedColumn, introspectedTable,
                    Plugin.ModelClassType.PRIMARY_KEY)) {
                topLevelClass.addField(field);
                topLevelClass.addImportedType(field.getType());
            }

            //同样，getter方法的注释在getJavaBeansGetter()方法中添加
            Method method = getJavaBeansGetter(introspectedColumn, context, introspectedTable);
            if (plugins.modelGetterMethodGenerated(method, topLevelClass,
                    introspectedColumn, introspectedTable,
                    Plugin.ModelClassType.PRIMARY_KEY)) {
                topLevelClass.addMethod(method);
            }

            if (!introspectedTable.isImmutable()) {
            	//同样，setter方法的注释在getJavaBeansGetter()方法中添加
                method = getJavaBeansSetter(introspectedColumn, context, introspectedTable);
                if (plugins.modelSetterMethodGenerated(method, topLevelClass,
                        introspectedColumn, introspectedTable,
                        Plugin.ModelClassType.PRIMARY_KEY)) {
                    topLevelClass.addMethod(method);
                }
            }
        }

        List<CompilationUnit> answer = new ArrayList<>();
        if (context.getPlugins().modelPrimaryKeyClassGenerated(
                topLevelClass, introspectedTable)) {
            answer.add(topLevelClass);
        }
        return answer;
    }

    private void addParameterizedConstructor(TopLevelClass topLevelClass) {
        Method method = new Method(topLevelClass.getType().getShortName());
        method.setVisibility(JavaVisibility.PUBLIC);
        method.setConstructor(true);
        context.getCommentGenerator().addGeneralMethodComment(method, introspectedTable);

        StringBuilder sb = new StringBuilder();
        for (IntrospectedColumn introspectedColumn : introspectedTable
                .getPrimaryKeyColumns()) {
            method.addParameter(new Parameter(introspectedColumn.getFullyQualifiedJavaType(),
                    introspectedColumn.getJavaProperty()));
            sb.setLength(0);
            sb.append("this."); //$NON-NLS-1$
            sb.append(introspectedColumn.getJavaProperty());
            sb.append(" = "); //$NON-NLS-1$
            sb.append(introspectedColumn.getJavaProperty());
            sb.append(';');
            method.addBodyLine(sb.toString());
        }

        topLevelClass.addMethod(method);
    }
}
