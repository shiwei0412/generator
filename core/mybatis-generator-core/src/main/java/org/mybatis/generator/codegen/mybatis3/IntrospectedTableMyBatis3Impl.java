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
package org.mybatis.generator.codegen.mybatis3;

import java.util.ArrayList;
import java.util.List;

import org.mybatis.generator.api.GeneratedJavaFile;
import org.mybatis.generator.api.GeneratedKotlinFile;
import org.mybatis.generator.api.GeneratedXmlFile;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.ProgressCallback;
import org.mybatis.generator.api.dom.java.CompilationUnit;
import org.mybatis.generator.api.dom.kotlin.KotlinFile;
import org.mybatis.generator.api.dom.xml.Document;
import org.mybatis.generator.codegen.AbstractGenerator;
import org.mybatis.generator.codegen.AbstractJavaClientGenerator;
import org.mybatis.generator.codegen.AbstractJavaGenerator;
import org.mybatis.generator.codegen.AbstractKotlinGenerator;
import org.mybatis.generator.codegen.AbstractXmlGenerator;
import org.mybatis.generator.codegen.mybatis3.javamapper.AnnotatedClientGenerator;
import org.mybatis.generator.codegen.mybatis3.javamapper.DqsClientGenerator;
import org.mybatis.generator.codegen.mybatis3.javamapper.JavaMapperGenerator;
import org.mybatis.generator.codegen.mybatis3.javamapper.MixedClientGenerator;
import org.mybatis.generator.codegen.mybatis3.model.BaseRecordGenerator;
import org.mybatis.generator.codegen.mybatis3.model.ExampleGenerator;
import org.mybatis.generator.codegen.mybatis3.model.PrimaryKeyGenerator;
import org.mybatis.generator.codegen.mybatis3.model.RecordWithBLOBsGenerator;
import org.mybatis.generator.codegen.mybatis3.xmlmapper.XMLMapperGenerator;
import org.mybatis.generator.config.PropertyRegistry;
import org.mybatis.generator.internal.ObjectFactory;
import org.mybatis.generator.internal.util.StringUtility;

/**
 * Introspected table implementation for generating MyBatis3 artifacts.
 * 
 * @author Jeff Butler
 */
public class IntrospectedTableMyBatis3Impl extends IntrospectedTable {
    
    protected List<AbstractJavaGenerator> javaGenerators = new ArrayList<>();

    protected List<AbstractKotlinGenerator> kotlinGenerators = new ArrayList<>();

    protected AbstractXmlGenerator xmlMapperGenerator;

    public IntrospectedTableMyBatis3Impl() {
        super(TargetRuntime.MYBATIS3);
    }

    @Override
    public void calculateGenerators(List<String> warnings,
            ProgressCallback progressCallback) {
    	//添加java模型生成器，对应javaModelGenerator配置，包括ExampleGenerator、PrimaryKeyGenerator、BaseRecordGenerator、RecordWithBLOBsGenerator
        calculateJavaModelGenerators(warnings, progressCallback);
        //添加java client生成器，对应javaClientGenerator配置
        //TODO shiwei dqs也可以弄一个DqsClientGenerator，然后里面再添加一下调用dqs的mysql模型的method、kylin的dqs模型的method
        AbstractJavaClientGenerator javaClientGenerator =
                calculateClientGenerators(warnings, progressCallback);
        //添加mapper生成器，对应sqlMapGenerator配置
        calculateXmlMapperGenerator(javaClientGenerator, warnings, progressCallback);
    }

    protected void calculateXmlMapperGenerator(AbstractJavaClientGenerator javaClientGenerator, 
            List<String> warnings,
            ProgressCallback progressCallback) {
    	//TODO shiwei 也要特殊处理，dqs模型不需要xmlMapperGenerator
        if(tableConfiguration != null) {
        	if(tableConfiguration.isDqsModel()) {
        		xmlMapperGenerator = null;
        		return;
        	}
        }
    	
        if (javaClientGenerator == null) {
            if (context.getSqlMapGeneratorConfiguration() != null) {
                xmlMapperGenerator = new XMLMapperGenerator();
            }
        } else {
            xmlMapperGenerator = javaClientGenerator.getMatchedXMLGenerator();
        }
        
        initializeAbstractGenerator(xmlMapperGenerator, warnings,
                progressCallback);
    }

    protected AbstractJavaClientGenerator calculateClientGenerators(List<String> warnings,
            ProgressCallback progressCallback) {
        if (!rules.generateJavaClient()) {
            return null;
        }
        
        AbstractJavaClientGenerator javaGenerator = createJavaClientGenerator();
        if (javaGenerator == null) {
            return null;
        }

        initializeAbstractGenerator(javaGenerator, warnings, progressCallback);
        javaGenerators.add(javaGenerator);

        return javaGenerator;
    }

    protected AbstractJavaClientGenerator createJavaClientGenerator() {
        if (context.getJavaClientGeneratorConfiguration() == null) {
            return null;
        }
        //TODO shiwei 如果该模型为dqs模型，isDqsModel存在且为true
        //第一次new MyBatisGenerator()的时候，此时tableConfiguration为空，此时不做如下判断。
        if(tableConfiguration != null) {
        	if(tableConfiguration.isDqsModel()) {
        		//如果该模型使用刑天框架，useXingtianExecutor存在且为true
        		if(tableConfiguration.isUseXingtianExecutor()) {
        			return new DqsClientGenerator(getClientProject());
        		}else {
        			return null;
        		}
        	}
        }
        
        String type = context.getJavaClientGeneratorConfiguration()
                .getConfigurationType();

        AbstractJavaClientGenerator javaGenerator;
        //TODO shiwei 增加一个dqsMAPPER处理dqs的请求，然后初始化DqsMapperGenerator
        if ("XMLMAPPER".equalsIgnoreCase(type)) { //$NON-NLS-1$
            javaGenerator = new JavaMapperGenerator(getClientProject());
        } else if ("MIXEDMAPPER".equalsIgnoreCase(type)) { //$NON-NLS-1$
            javaGenerator = new MixedClientGenerator(getClientProject());
        } else if ("ANNOTATEDMAPPER".equalsIgnoreCase(type)) { //$NON-NLS-1$
            javaGenerator = new AnnotatedClientGenerator(getClientProject());
        } else if ("MAPPER".equalsIgnoreCase(type)) { //$NON-NLS-1$
            javaGenerator = new JavaMapperGenerator(getClientProject());
        } else {
            javaGenerator = (AbstractJavaClientGenerator) ObjectFactory
                    .createInternalObject(type);
        }

        return javaGenerator;
    }

    protected void calculateJavaModelGenerators(List<String> warnings,
            ProgressCallback progressCallback) {
        if (getRules().generateExampleClass()) {
            AbstractJavaGenerator javaGenerator = new ExampleGenerator(getExampleProject());
            initializeAbstractGenerator(javaGenerator, warnings,
                    progressCallback);
            javaGenerators.add(javaGenerator);
        }

        if (getRules().generatePrimaryKeyClass()) {
            AbstractJavaGenerator javaGenerator = new PrimaryKeyGenerator(getModelProject());
            initializeAbstractGenerator(javaGenerator, warnings,
                    progressCallback);
            javaGenerators.add(javaGenerator);
        }

        if (getRules().generateBaseRecordClass()) {
            AbstractJavaGenerator javaGenerator = new BaseRecordGenerator(getModelProject());
            initializeAbstractGenerator(javaGenerator, warnings,
                    progressCallback);
            javaGenerators.add(javaGenerator);
        }

        if (getRules().generateRecordWithBLOBsClass()) {
            AbstractJavaGenerator javaGenerator = new RecordWithBLOBsGenerator(getModelProject());
            initializeAbstractGenerator(javaGenerator, warnings,
                    progressCallback);
            javaGenerators.add(javaGenerator);
        }
    }

    protected void initializeAbstractGenerator(
            AbstractGenerator abstractGenerator, List<String> warnings,
            ProgressCallback progressCallback) {
        if (abstractGenerator == null) {
            return;
        }

        abstractGenerator.setContext(context);
        abstractGenerator.setIntrospectedTable(this);
        abstractGenerator.setProgressCallback(progressCallback);
        abstractGenerator.setWarnings(warnings);
    }

    @Override
    public List<GeneratedJavaFile> getGeneratedJavaFiles() {
        List<GeneratedJavaFile> answer = new ArrayList<>();
        //前面的calculateGenerators()方法中，将model、client的生成器都放在javaGenerators中了
        //AbstractJavaGenerator是所有java生成器的抽象父类，重点关注JavaMapperGenerator、PrimaryKeyGenerator、BaseRecordGenerator等。
        for (AbstractJavaGenerator javaGenerator : javaGenerators) {
        	//生成编译单元
            List<CompilationUnit> compilationUnits = javaGenerator.getCompilationUnits();
            for (CompilationUnit compilationUnit : compilationUnits) {
                GeneratedJavaFile gjf = new GeneratedJavaFile(compilationUnit,
                                javaGenerator.getProject(),
                                context.getProperty(PropertyRegistry.CONTEXT_JAVA_FILE_ENCODING),
                                context.getJavaFormatter());
                answer.add(gjf);
            }
        }

        return answer;
    }

    @Override
    public List<GeneratedKotlinFile> getGeneratedKotlinFiles() {
        List<GeneratedKotlinFile> answer = new ArrayList<>();

        for (AbstractKotlinGenerator kotlinGenerator : kotlinGenerators) {
            List<KotlinFile> kotlinFiles = kotlinGenerator.getKotlinFiles();
            for (KotlinFile kotlinFile : kotlinFiles) {
                GeneratedKotlinFile gjf = new GeneratedKotlinFile(kotlinFile,
                                kotlinGenerator.getProject(),
                                context.getProperty(PropertyRegistry.CONTEXT_KOTLIN_FILE_ENCODING),
                                context.getKotlinFormatter());
                answer.add(gjf);
            }
        }

        return answer;
    }

    protected String getClientProject() {
        return context.getJavaClientGeneratorConfiguration().getTargetProject();        
    }

    protected String getModelProject() {
        return context.getJavaModelGeneratorConfiguration().getTargetProject();        
    }
    
    protected String getExampleProject() {
        String project = context.getJavaModelGeneratorConfiguration().getProperty(
                PropertyRegistry.MODEL_GENERATOR_EXAMPLE_PROJECT);
        
        if (StringUtility.stringHasValue(project)) {
            return project;
        } else {
            return getModelProject();
        }
    }
    
    @Override
    public List<GeneratedXmlFile> getGeneratedXmlFiles() {
        List<GeneratedXmlFile> answer = new ArrayList<>();

        if (xmlMapperGenerator != null) {
            Document document = xmlMapperGenerator.getDocument();
            GeneratedXmlFile gxf = new GeneratedXmlFile(document,
                    getMyBatis3XmlMapperFileName(), getMyBatis3XmlMapperPackage(),
                    context.getSqlMapGeneratorConfiguration().getTargetProject(),
                    true, context.getXmlFormatter());
            if (context.getPlugins().sqlMapGenerated(gxf, this)) {
                answer.add(gxf);
            }
        }

        return answer;
    }

    @Override
    public int getGenerationSteps() {
        return javaGenerators.size()
                + (xmlMapperGenerator == null ? 0 : 1);
    }

    @Override
    public boolean requiresXMLGenerator() {
        AbstractJavaClientGenerator javaClientGenerator =
                createJavaClientGenerator();
        
        if (javaClientGenerator == null) {
            return false;
        } else {
            return javaClientGenerator.requiresXMLGenerator();
        }
    }
}
