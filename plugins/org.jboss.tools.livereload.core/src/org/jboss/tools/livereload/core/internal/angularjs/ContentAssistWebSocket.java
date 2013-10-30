/******************************************************************************* 
 * Copyright (c) 2013 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
package org.jboss.tools.livereload.core.internal.angularjs;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;

import org.eclipse.jetty.websocket.WebSocket;

/**
 * @author Alexey Kazakov
 */
public class ContentAssistWebSocket implements WebSocket.OnTextMessage {

	private Connection connection;
	private ServletContext context;
	private Map<UUID, BlockingQueue<String>> resultMap = new ConcurrentHashMap<UUID, BlockingQueue<String>>();

	public ContentAssistWebSocket(ServletContext context) {
		this.context = context;
	}

	@Override
	public void onOpen(Connection connection) {
		this.connection = connection;
		Set<ContentAssistWebSocket> browserCliWebSockets = getBrowserCliWebSocketsList();
		browserCliWebSockets.add(this);
System.out.println("A client connected.");
	}

	@SuppressWarnings("unchecked")
	private Set<ContentAssistWebSocket> getBrowserCliWebSocketsList() {
		return (Set<ContentAssistWebSocket>) context.getAttribute("org.jboss.tools.jst.ContentAssistWebSockets");
	}

	@Override
	public void onClose(int closeCode, String message) {
		Set<ContentAssistWebSocket> browserCliWebSockets = getBrowserCliWebSocketsList();
		browserCliWebSockets.remove(this);
System.out.println("A client disconnected.");
	}

	@Override
	public void onMessage(String data) {
		int separatorIndex = data.indexOf(';');
		String id = data.substring(0, separatorIndex);
		String result = data.substring(separatorIndex + 1);

		BlockingQueue<String> resultQueue = resultMap.get(UUID.fromString(id));
		if (resultQueue != null) {
			resultQueue.offer(result);
		} else {
System.err.println("Unknown/outdated message: " + data);
		}
	}

	public String evaluate(String expression, int timeout) throws IOException, InterruptedException {
		UUID id = UUID.randomUUID();
		connection.sendMessage(id.toString() + ";" + expression);
		BlockingQueue<String> resultQueue = new ArrayBlockingQueue<>(1);
		resultMap.put(id, resultQueue);
		try {
			String result = resultQueue.poll(timeout, TimeUnit.MILLISECONDS);
			return result;
		} finally {
			resultMap.remove(id);
		}
	}
}