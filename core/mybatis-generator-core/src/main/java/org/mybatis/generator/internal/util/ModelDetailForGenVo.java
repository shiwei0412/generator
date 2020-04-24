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
package org.mybatis.generator.internal.util;

import java.io.Serializable;
import java.util.List;

public class ModelDetailForGenVo implements Serializable{
	private static final long serialVersionUID = 1L;
	private String name;
	private String tableName;
	private String modelName;
	private List<ModelFieldDetailForGenVo> fields;
	private List<ModelFieldDetailForGenVo> metrics;
	private String description;
	private String location;
	private Boolean isRealTime;
	
	private Integer createId;
	private String fullName;
	private String email;
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getTableName() {
		return tableName;
	}
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}
	public String getModelName() {
		return modelName;
	}
	public void setModelName(String modelName) {
		this.modelName = modelName;
	}
	public List<ModelFieldDetailForGenVo> getFields() {
		return fields;
	}
	public void setFields(List<ModelFieldDetailForGenVo> fields) {
		this.fields = fields;
	}
	public List<ModelFieldDetailForGenVo> getMetrics() {
		return metrics;
	}
	public void setMetrics(List<ModelFieldDetailForGenVo> metrics) {
		this.metrics = metrics;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
	}
	public Boolean getIsRealTime() {
		return isRealTime;
	}
	public void setIsRealTime(Boolean isRealTime) {
		this.isRealTime = isRealTime;
	}
	public Integer getCreateId() {
		return createId;
	}
	public void setCreateId(Integer createId) {
		this.createId = createId;
	}
	public String getFullName() {
		return fullName;
	}
	public void setFullName(String fullName) {
		this.fullName = fullName;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
}
