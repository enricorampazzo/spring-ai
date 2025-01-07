package org.springframework.ai.chat.memory.neo4j;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Transaction;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.MediaContent;

import java.util.*;

public class Neo4jChatMemory implements ChatMemory {

	private final Driver driver;
	private final String assistantLabel;
	private final String userLabel;
	private final String sessionLabel;

	public Neo4jChatMemory(Driver driver, String assistantLabel, String userLabel, String sessionLabel) {
		this.driver = driver;
		this.assistantLabel = assistantLabel;
		this.userLabel = userLabel;
		this.sessionLabel = sessionLabel;
	}

	public static Neo4jChatMemory create(Neo4jChatMemoryConfig config) {
		return new Neo4jChatMemory(config.getDriver(), config.getAssistantLabel(), config.getUserLabel(),
				config.getSessionLabel());
	}

	@Override
	public void add(String conversationId, Message message) {
		add(conversationId, List.of(message));
	}

	private void addMessageToTransaction(Transaction t, String conversationId, Message message) {
		Map<String, Object> queryParameters = new HashMap<>();
		queryParameters.put("conversationId", conversationId);
		StringBuilder statementBuilder = new StringBuilder();
		boolean withMsg = false;
		statementBuilder.append("MERGE (s:Session {id:$conversationId}) WITH s\n");
		statementBuilder.append("MATCH (s)-[:HAS_MESSAGE]->(countMsg:Message WITH coalesce(count(countMsg), 0) as totalMsg");
		statementBuilder.append("MERGE (s)-[:HAS_MESSAGE]->(msg:Message) ON CREATE SET msg = $messageProperties\n SET msg.idx = totalMsg + 1");
		Map<String, Object> attributes = new HashMap<>();

		attributes.put(MessageAttributes.MESSAGE_TYPE.getValue(), message.getMessageType().getValue());
		attributes.put(MessageAttributes.TEXT_CONTENT.getValue(), message.getText());
		queryParameters.put("messageProperties", attributes);
		if (!Optional.ofNullable(message.getMetadata()).orElse(Map.of()).isEmpty()) {
			statementBuilder.append("WITH msg\n");
			withMsg = true;
			statementBuilder.append("MERGE (metadataNode:Metadata {id: msg.id})\n");
			statementBuilder.append("ON CREATE SET metadataNode = $metadata");
			statementBuilder.append("MERGE (msg)-[:HAS_METADATA]->(metadataNode)");
			queryParameters.put("metadata", message.getMetadata());
		}

		if (message instanceof ToolResponseMessage toolResponseMessage) {
			if (!withMsg) {
				statementBuilder.append("WITH msg\n");
				withMsg = true;
			}
			List<ToolResponseMessage.ToolResponse> toolResponses = toolResponseMessage.getResponses();
			List<Map<String, String>> toolResponseMaps = new ArrayList<>();
			for (int i = 0; i < Optional.ofNullable(toolResponses).orElse(List.of()).size(); i++) {
				var toolResponse = toolResponses.get(i);
				Map<String, String> toolResponseMap = Map.of("id", toolResponse.id(),
						"name", toolResponse.name(),
						"responseData", toolResponse.responseData(),
						"idx", Integer.toString(i));
				toolResponseMaps.add(toolResponseMap);
			}

			statementBuilder.append("FOREACH tr IN $toolResponses | CREATE (tm:ToolMessage)\n");
			statementBuilder.append("SET tm = tr MERGE (msg)-[:HAS_MESSAGE]->(tm)");
		}


		if (message instanceof MediaContent messageWithMedia) {
			if (messageWithMedia.getMedia() != null && !messageWithMedia.getMedia().isEmpty()) {
				List<Map<String, Object>> mediaNodes = convertMedia(messageWithMedia.getMedia());
				statementBuilder.append("UNWIND $media AS m\nMERGE (media:Media {id:m.id}) ON MATCH SET media = m\n");
				statementBuilder.append("WITH msg, media MERGE (msg)-[:HAS_MEDIA]->(media)");
				queryParameters.put("media", mediaNodes);
			}
		}
		t.run(statementBuilder.toString(), queryParameters);
	}

	private List<Map<String, Object>> convertMedia(List<Media> media) {
		return media.stream().map(m -> Map.<String, Object>of(MediaAttributes.ID.getValue(), m.getId(),
				MediaAttributes.MIME_TYPE.getValue(), m.getMimeType().toString(),
				MediaAttributes.NAME.getValue(), m.getName(),
				MediaAttributes.DATA.getValue(), m.getDataAsByteArray())
		).toList();
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		try(Transaction t = driver.session().beginTransaction()){
			for(Message m : messages) {
				addMessageToTransaction(t, conversationId, m);
			}
			t.commit();
		}
	}

	@Override
	public List<Message> get(String conversationId, int lastN) {
		return List.of();
	}

	@Override
	public void clear(String conversationId) {

	}

}
