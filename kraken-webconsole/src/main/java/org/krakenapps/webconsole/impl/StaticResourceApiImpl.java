/*
 * Copyright 2011 Future Systems
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.krakenapps.webconsole.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Validate;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.krakenapps.util.DirectoryMap;
import org.krakenapps.webconsole.StaticResourceApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(name = "static-resource-api")
@Provides
public class StaticResourceApiImpl implements StaticResourceApi {
	private static final long serialVersionUID = 1L;
	private Logger logger = LoggerFactory.getLogger(StaticResourceApiImpl.class);
	private static final String CONTEXT_ITEM = "/context";
	private DirectoryMap<HttpServlet> directoryMap;

	@Validate
	public void start() {
		directoryMap = new DirectoryMap<HttpServlet>();
	}

	@Override
	public Collection<String> getPrefixes() {
		Set<String> prefixes = new HashSet<String>();
		Set<Entry<String, HttpServlet>> entries = directoryMap.entrySet();
		for (Entry<String, HttpServlet> entry : entries) {
			String key = entry.getKey();
			String prefix = key.substring(0, key.length() - CONTEXT_ITEM.length());
			if (prefix.length() == 0)
				prefix = "/";

			prefixes.add(prefix);
		}

		return prefixes;
	}

	@Override
	public HttpServlet getContext(String prefix) {
		if (prefix.equals("/"))
			prefix = "";

		return directoryMap.get(prefix + CONTEXT_ITEM);
	}

	@Override
	public void service(ChannelHandlerContext ctx, HttpRequest req) {
		try {
			serviceInternal(ctx, req, true);
		} catch (ServletException e) {
			logger.error("kraken webconsole: servlet service error.", e);
		} catch (IOException e) {
			logger.error("kraken webconsole: io error.", e);
		}
	}

	private void serviceInternal(ChannelHandlerContext ctx, HttpRequest req, boolean service) throws ServletException,
			IOException {
		ServletResponse response = null;
		try {
			String servletPath = findServlet(req.getUri());
			if (servletPath == null)
				throw new IllegalArgumentException("invalid request path");

			HttpServlet servlet = directoryMap.get(servletPath + "context");

			String requestPath = req.getUri().substring(servletPath.length());
			if (requestPath.isEmpty()) {
				requestPath = "index.html";
			}

			ServletRequest request = new Request(ctx, req, servletPath, requestPath);
			response = new Response(ctx, req, service);
			response.setContentType(getMimeType(requestPath));

			servlet.service(request, response);

			response.getOutputStream().flush();
		} catch (IOException e) {
			if (!e.getMessage().equals("403 Forbidden"))
				throw e;

			if (req.getUri().endsWith("/")) {
				HttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
				HttpHeaders.setContentLength(resp, resp.getContent().readableBytes());
				ctx.getChannel().write(resp);
			} else {
				req.setUri(req.getUri() + "/");
				serviceInternal(ctx, req, false);
				HttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.MOVED_PERMANENTLY);
				resp.setHeader(HttpHeaders.Names.LOCATION, req.getUri());
				HttpHeaders.setContentLength(resp, resp.getContent().readableBytes());
				ctx.getChannel().write(resp);
			}
		} finally {
			response.getOutputStream().close();
		}
	}

	private String findServlet(String path) {
		String[] tokens = split(path);

		String prefix = null;
		String dir = "/";

		// default
		if (directoryMap.containsKey("/context"))
			prefix = dir;

		// longest match
		for (int i = 0; i < tokens.length; i++) {
			dir += tokens[i] + "/";
			if (directoryMap.containsKey(dir + "context"))
				prefix = dir;
		}

		return prefix;
	}

	private String[] split(String path) {
		path = path.substring(0, path.lastIndexOf("/"));
		String[] s = path.split("/");

		int count = 0;
		for (int i = 0; i < s.length; i++)
			if (!s[i].isEmpty())
				count++;

		String[] n = new String[count];
		int j = 0;
		for (int i = 0; i < s.length; i++)
			if (!s[i].isEmpty())
				n[j++] = s[i];

		return n;
	}

	private String getMimeType(String path) {
		String mimeType = MimeTypes.instance().getByFile(path);

		if (mimeType == null)
			mimeType = "text/html";

		if (mimeType.startsWith("text/"))
			mimeType += "; charset=utf-8";

		return mimeType;
	}

	@Override
	public void register(String prefix, HttpServlet ctx) {
		if (prefix.equals("/"))
			prefix = "";

		HttpServlet old = directoryMap.putIfAbsent(prefix + CONTEXT_ITEM, ctx);
		if (old != null)
			throw new IllegalStateException(prefix + " already exists");
	}

	@Override
	public void unregister(String prefix) {
		if (prefix == null)
			throw new IllegalArgumentException("prefix should be not null");

		if (prefix.equals("/"))
			prefix = "";

		directoryMap.remove(prefix + CONTEXT_ITEM);
	}
}
