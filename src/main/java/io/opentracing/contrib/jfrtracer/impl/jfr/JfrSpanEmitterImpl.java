/*
 * Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentracing.contrib.jfrtracer.impl.jfr;

import com.oracle.jrockit.jfr.EventDefinition;
import com.oracle.jrockit.jfr.EventToken;
import com.oracle.jrockit.jfr.TimedEvent;
import com.oracle.jrockit.jfr.ValueDefinition;

import io.opentracing.Span;
import io.opentracing.contrib.jfrtracer.impl.jfr.JfrScopeEmitterImpl;

/**
 * This is the JDK 7/8 implementation for emitting Span events. For the JDK 9 and later
 * implementation, see src/main/java9.
 */
@SuppressWarnings("deprecation")
final class JfrSpanEmitterImpl extends AbstractJfrSpanEmitterImpl {
	private static final EventToken SPAN_EVENT_TOKEN;
	private SpanEvent currentEvent;

	static {
		SPAN_EVENT_TOKEN = JfrScopeEmitterImpl.register(SpanEvent.class);
	}

	@EventDefinition(path = "jfrtracer/spanevent", name = "SpanEvent", description = "And event representing an OpenTracing span", stacktrace = false, thread = true)
	private static class SpanEvent extends TimedEvent {
		@ValueDefinition(name = "OperationName")
		private String operationName;
		
		@ValueDefinition(name = "TraceId")
		private String traceId;

		@ValueDefinition(name = "SpanId")
		private String spanId;

		@ValueDefinition(name = "ParentId")
		private String parentId;

		@ValueDefinition(name = "StartThread", description = "The thread initiating the span")
		private Thread startThread;

		@ValueDefinition(name = "EndThread", description = "The thread ending the span")
		private Thread endThread;

		public SpanEvent(EventToken eventToken) {
			super(eventToken);
		}

		@SuppressWarnings("unused")
		public String getTraceId() {
			return traceId;
		}

		@SuppressWarnings("unused")
		public String getSpanId() {
			return spanId;
		}

		@SuppressWarnings("unused")
		public String getParentId() {
			return parentId;
		}

		@SuppressWarnings("unused")
		public Thread getStartThread() {
			return startThread;
		}

		@SuppressWarnings("unused")
		public Thread getEndThread() {
			return endThread;
		}
	}

	private static class EndEventCommand implements Runnable {
		private final SpanEvent event;

		public EndEventCommand(SpanEvent event) {
			this.event = event;
		}

		@Override
		public void run() {
			event.end();
			event.commit();
		}
	}

	private static class BeginEventCommand implements Runnable {
		private final SpanEvent event;

		public BeginEventCommand(SpanEvent event) {
			this.event = event;
		}

		@Override
		public void run() {
			event.begin();
		}
	}

	JfrSpanEmitterImpl(Span span) {
		super(span);
	}

	@Override
	public void close() {
		if (currentEvent != null) {
			if (currentEvent.shouldWrite()) {
				currentEvent.endThread = Thread.currentThread();
				EXECUTOR.execute(new EndEventCommand(currentEvent));
				currentEvent = null;
			}
		} else {
			LOGGER.warning("Close without start discovered!");
		}
	}

	@Override
	public void start(String operationName) {
		currentEvent = new SpanEvent(SPAN_EVENT_TOKEN);
		if (currentEvent.getEventInfo().isEnabled()) {
			currentEvent.operationName = operationName;
			currentEvent.startThread = Thread.currentThread();
			currentEvent.traceId = span.context().toTraceId();
			currentEvent.spanId = span.context().toSpanId();
			// currentEvent.parentId = span.toParentId();
		}
		EXECUTOR.execute(new BeginEventCommand(currentEvent));
	}

	@Override
	public String toString() {
		return "JDK 7 & JDK 8 JFR Span Emitter";
	}
}