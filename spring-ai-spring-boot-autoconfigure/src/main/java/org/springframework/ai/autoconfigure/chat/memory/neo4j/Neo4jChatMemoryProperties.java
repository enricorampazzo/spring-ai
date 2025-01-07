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

package org.springframework.ai.autoconfigure.chat.memory.neo4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.autoconfigure.chat.memory.CommonChatMemoryProperties;
import org.springframework.ai.chat.memory.neo4j.Neo4jChatMemoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

import java.time.Duration;

/**
 * Configuration properties for Cassandra chat memory.
 *
 * @author Mick Semb Wever
 * @author Jihoon Kim
 * @since 1.0.0
 */
@ConfigurationProperties(Neo4jChatMemoryProperties.CONFIG_PREFIX)
public class Neo4jChatMemoryProperties extends CommonChatMemoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.neo4j";

	private static final Logger logger = LoggerFactory.getLogger(Neo4jChatMemoryProperties.class);

	private String assistantLabel = Neo4jChatMemoryConfig.DEFAULT_ASSISTANT_LABEL;

	private String userLabel = Neo4jChatMemoryConfig.DEFAULT_USER_LABEL;

	private Duration timeToLive = null;

	public String getAssistantLabel() {
		return this.assistantLabel;
	}

	public void setAssistantLabel(String assistantLabel) {
		this.assistantLabel = assistantLabel;
	}

	public String getUserLabel() {
		return this.userLabel;
	}

	public void setUserLabel(String userLabel) {
		this.userLabel = userLabel;
	}

	@Nullable
	public Duration getTimeToLive() {
		return this.timeToLive;
	}

	public void setTimeToLive(Duration timeToLive) {
		this.timeToLive = timeToLive;
	}

}
