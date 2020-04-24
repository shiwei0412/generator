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

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;



/**
 * Http client
 * 
 * @author zhoujianming
 *
 */
public class HttpHelper {
	
	private final int CONNECT_TIMEOUT = 4000;
	private final int CONNECTION_REQUEST_TIMEOUT = 4000;
	private final int SOCKET_TIMEOUT = 10000;
	
	private final int MAX_TOTAL = 200;
	private final int MAX_PER_ROUTE = 50;

	public static final String CHARSET = "UTF-8";

	private CloseableHttpClient httpClient;
    
    public HttpHelper() {
    	init(MAX_PER_ROUTE, SOCKET_TIMEOUT);
    }
    
    public HttpHelper(Integer maxPerRoute, Integer socketTimeout) {
    	init(maxPerRoute, socketTimeout);
    }
    
    /**
     * @param maxPerRoute 每个路由的最大连接数
     * @param socketTimeout 超时时间
     */
    private void init(Integer maxPerRoute, Integer socketTimeout) {
      	int _socketTimeout = socketTimeout != null ? socketTimeout : SOCKET_TIMEOUT;
    	int _maxPerRoute = maxPerRoute != null ? maxPerRoute : MAX_PER_ROUTE;
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(MAX_TOTAL);
        cm.setDefaultMaxPerRoute(_maxPerRoute);
        httpClient = HttpClients.custom()
        		.setConnectionManager(cm)
        		.setDefaultRequestConfig(RequestConfig.custom()
	                .setConnectionRequestTimeout(CONNECT_TIMEOUT)
	                .setConnectTimeout(CONNECTION_REQUEST_TIMEOUT)
	                .setSocketTimeout(_socketTimeout)
	                .build())
        		.build();
    }

	public HttpRequestResult postRequest(String url, String requestBody) throws Exception {
		HttpPost httpPost = new HttpPost(url);
		StringEntity reqEntity = new StringEntity(requestBody, "utf-8");
		reqEntity.setContentType("application/json");
		httpPost.setHeader("D-DES-AppKey","youdata");
		httpPost.setEntity(reqEntity);

		try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);
			return new HttpRequestResult(response.getStatusLine().getStatusCode(), result);
		} catch (Exception e) {
			throw e;
		}
	}

	public HttpRequestResult getRequest(String url, Map<String, Object> params) throws Exception {

		if (StringUtils.isBlank(url)) {
			throw new IllegalArgumentException("postUrl can't be blank!");
		}

		List<String> paramList = new ArrayList<String>();
		if (params != null && !params.isEmpty()) {
			for (Entry<String, Object> entry : params.entrySet()) {
				if(entry.getValue() != null) {
					paramList.add(entry.getKey() + "=" + URLEncoder.encode(entry.getValue()+"", CHARSET));
				}
			}
		}

		url = url + "?"  + StringUtils.join(paramList, "&");
		HttpGet httpGet = new HttpGet(url);

		try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);
			return new HttpRequestResult(response.getStatusLine().getStatusCode(), result);
		} catch (Exception e) {
			throw e;
		}

	}

	
}
