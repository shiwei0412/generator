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

/**
 * Created by wangwei on 2017/7/19.
 */
public class HttpRequestResult {

	private int httpStatusCode;

	private String responseBody;

	public HttpRequestResult(int statusCode, String responseBody) {
		this.httpStatusCode = statusCode;
		this.responseBody = responseBody;
	}

	public int getHttpStatusCode() {
		return httpStatusCode;
	}

	public String getResponseBody() {
		return responseBody;
	}

}