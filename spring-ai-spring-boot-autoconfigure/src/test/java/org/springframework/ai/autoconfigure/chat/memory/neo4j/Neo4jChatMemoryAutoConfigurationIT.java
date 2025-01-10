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
import org.springframework.ai.chat.memory.neo4j.Neo4jChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.Media;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.MimeType;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mick Semb Wever
 * @author Jihoon Kim
 * @since 1.0.0
 */
@Testcontainers
class Neo4jChatMemoryAutoConfigurationIT {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("neo4j");

	@SuppressWarnings({"rawtypes", "resource"})
	@Container
	static Neo4jContainer neo4jContainer = (Neo4jContainer) new Neo4jContainer(DEFAULT_IMAGE_NAME.withTag("5")).withoutAuthentication().withExposedPorts(7474,7687);

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

				memory.add(sessionId, List.of(new UserMessage("test question"), new AssistantMessage("test answer", Map.of(), List.of(new AssistantMessage.ToolCall("id", "type", "name", "arguments")))));

				assertThat(memory.get(sessionId, Integer.MAX_VALUE)).hasSize(2);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(1).getMessageType())
					.isEqualTo(MessageType.USER);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(1).getText()).isEqualTo("test question");
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(0).getMessageType())
					.isEqualTo(MessageType.ASSISTANT);
				assertThat(memory.get(sessionId, Integer.MAX_VALUE).get(0).getText()).isEqualTo("test answer");
				assertThat(((AssistantMessage) memory.get(sessionId, Integer.MAX_VALUE).get(0)).getToolCalls().size()).isEqualTo(1);
				assertThat(((AssistantMessage) memory.get(sessionId, Integer.MAX_VALUE).get(0)).getToolCalls().get(0)).isEqualTo(new AssistantMessage.ToolCall("id", "type", "name", "arguments"));
				memory.add(sessionId, new UserMessage("Message with media", List.of(Media.builder()
						.name("some media").id(UUIDs.random().toString()).mimeType(MimeType.valueOf("text/plain"))
						.data("hello".getBytes(StandardCharsets.UTF_8)).build(),
						Media.builder().data(URI.create("http://www.google.com").toURL())
								.mimeType(MimeType.valueOf("text/plain")).build())));
				List<Message> messages = memory.get(sessionId, 1);
				assert messages.size() == 1;
				assertThat(messages.size()).isEqualTo(1);
				assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
				assertThat(((UserMessage)messages.get(0)).getMedia()).hasSize(2);
				assertThat(((UserMessage) messages.get(0)).getMedia().get(0).getData())
						.isEqualTo("http://www.google.com");
				assertThat(((UserMessage) messages.get(0)).getMedia().get(1).getData())
						.isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
				assertThat(((UserMessage) messages.get(0)).getMedia().get(1).getName()).isEqualTo("some media");

			});
	}



}
