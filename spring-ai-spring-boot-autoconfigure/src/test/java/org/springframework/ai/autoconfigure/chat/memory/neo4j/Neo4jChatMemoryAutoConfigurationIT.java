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

import com.datastax.driver.core.utils.UUIDs;
import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.chat.memory.cassandra.CassandraChatMemoryProperties;
import org.springframework.ai.chat.memory.cassandra.CassandraChatMemory;
import org.springframework.ai.chat.memory.neo4j.Neo4jChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mick Semb Wever
 * @author Jihoon Kim
 * @since 1.0.0
 */
@Testcontainers
class Neo4jChatMemoryAutoConfigurationIT {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("neo4j");

	@Container
	static Neo4jContainer neo4jContainer = new Neo4jContainer(DEFAULT_IMAGE_NAME.withTag("5"));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(
				AutoConfigurations.of(Neo4jChatMemoryAutoConfiguration.class, Neo4jAutoConfiguration.class));


	@Test
	void addAndGet() {
		this.contextRunner.withPropertyValues("spring.neo4j.uri=" + neo4jContainer.getBoltUrl(),
						"spring.ai.chat.memory.neo4j.assistantLabel", "AssistantMsg",
						"spring.ai.chat.memory.neo4j.userLabel", "UserMsg")
				.run(context -> {
				Neo4jChatMemory memory = context.getBean(Neo4jChatMemory.class);

				String sessionId = UUIDs.timeBased().toString();
				assertThat(memory.get(sessionId, Integer.MAX_VALUE)).isEmpty();

				memory.add(sessionId, new UserMessage("test question"));

				assertThat(memory.get(sessionId, Integer.MAX_VALUE)).hasSize(1);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(0).getMessageType())
					.isEqualTo(MessageType.USER);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(0).getText()).isEqualTo("test question");

				memory.clear(sessionId);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE)).isEmpty();

				memory.add(sessionId, List.of(new UserMessage("test question"), new AssistantMessage("test answer")));

				assertThat(memory.get(sessionId, Integer.MAX_VALUE)).hasSize(2);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(1).getMessageType())
					.isEqualTo(MessageType.USER);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(1).getText()).isEqualTo("test question");
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(0).getMessageType())
					.isEqualTo(MessageType.ASSISTANT);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(0).getText()).isEqualTo("test answer");

				CassandraChatMemoryProperties properties = context.getBean(CassandraChatMemoryProperties.class);
				assertThat(properties.getTimeToLive()).isEqualTo(getTimeToLive());
			});
	}

	@Test
	void compareTimeToLive_ISO8601Format() {
		this.contextRunner.withPropertyValues("spring.cassandra.contactPoints=" + getContactPointHost())
			.withPropertyValues("spring.cassandra.port=" + getContactPointPort())
			.withPropertyValues("spring.cassandra.localDatacenter=" + neo4jContainer.getLocalDatacenter())
			.withPropertyValues("spring.ai.chat.memory.cassandra.time-to-live=" + getTimeToLiveString())
			.run(context -> {
				CassandraChatMemoryProperties properties = context.getBean(CassandraChatMemoryProperties.class);
				assertThat(properties.getTimeToLive()).isEqualTo(Duration.parse(getTimeToLiveString()));
			});
	}

	private String getContactPointHost() {
		return neo4jContainer.getContactPoint().getHostString();
	}

	private String getContactPointPort() {
		return String.valueOf(neo4jContainer.getContactPoint().getPort());
	}

	private Duration getTimeToLive() {
		return Duration.ofSeconds(12000);
	}

	private String getTimeToLiveString() {
		return "PT1M";
	}

}
