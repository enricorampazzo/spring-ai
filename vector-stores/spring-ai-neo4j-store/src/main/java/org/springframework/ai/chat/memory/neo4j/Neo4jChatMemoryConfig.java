/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.chat.memory.neo4j;

import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Configuration for the Neo4j Chat Memory store.
 *
 * @author Enrico Rampazzo
 */
public final class Neo4jChatMemoryConfig {

	// todo – make configurable

	public static final String DEFAULT_ASSISTANT_LABEL = "AssistantMessage";

	public static final String DEFAULT_USER_LABEL = "UserMessage";

	public static final String DEFAULT_SESSION_LABEL = "Session";

	private static final Logger logger = LoggerFactory.getLogger(Neo4jChatMemoryConfig.class);

	private final Driver driver;

	private final Integer timeToLiveSeconds;
	private final String assistantLabel;
	private final String userLabel;
	private final String sessionLabel;

	public Driver getDriver() {
		return driver;
	}

	public Integer getTimeToLiveSeconds() {
		return timeToLiveSeconds;
	}

	public String getAssistantLabel() {
		return assistantLabel;
	}

	public String getUserLabel() {
		return userLabel;
	}

	public String getSessionLabel() {
		return sessionLabel;
	}

	private Neo4jChatMemoryConfig(Builder builder) {
		this.driver = builder.driver;
		this.timeToLiveSeconds = builder.timeToLiveSeconds;
		this.assistantLabel = builder.assistantLabel;
		this.userLabel = builder.userLabel;
		this.sessionLabel = builder.sessionLabel;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private Driver driver = null;

		private String assistantLabel = DEFAULT_ASSISTANT_LABEL;

		private String userLabel = DEFAULT_USER_LABEL;

		private String sessionLabel = DEFAULT_SESSION_LABEL;

		private Integer timeToLiveSeconds = null;


		private Builder() {
		}

		public Driver getDriver() {
			return driver;
		}

		public String getAssistantLabel() {
			return assistantLabel;
		}

		public String getUserLabel() {
			return userLabel;
		}

		public Integer getTimeToLiveSeconds() {
			return timeToLiveSeconds;
		}

		public Builder withDriver(Driver driver) {
			this.driver = driver;
			return this;
		}

		public Builder withAssistantLabelName(String name) {
			this.assistantLabel = name;
			return this;
		}

		public Builder withUserLabelName(String name) {
			this.userLabel = name;
			return this;
		}

		/** How long are messages kept for */
		public Builder withTimeToLive(Duration timeToLive) {
			if(timeToLive.isNegative()){
				throw new IllegalArgumentException("timeToLive cannot be negative");
			}
			this.timeToLiveSeconds = (int) timeToLive.toSeconds();
			return this;
		}

		public Neo4jChatMemoryConfig build() {
			return new Neo4jChatMemoryConfig(this);
		}

	}

}
