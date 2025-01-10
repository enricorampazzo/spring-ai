package org.springframework.ai.chat.memory.neo4j;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Transaction;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.MediaContent;
import org.springframework.util.MimeType;

import java.net.MalformedURLException;
import java.net.URI;
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
		statementBuilder.append("MERGE (s:Session {id:$conversationId}) WITH s\n");
		statementBuilder.append("OPTIONAL MATCH (s)-[:HAS_MESSAGE]->(countMsg:Message) WITH coalesce(count(countMsg), 0) as totalMsg, s\n");
		statementBuilder.append("CREATE (s)-[:HAS_MESSAGE]->(msg:Message) SET msg = $messageProperties\n SET msg.idx = totalMsg + 1\n");
		Map<String, Object> attributes = new HashMap<>();

		attributes.put(MessageAttributes.MESSAGE_TYPE.getValue(), message.getMessageType().getValue());
		attributes.put(MessageAttributes.TEXT_CONTENT.getValue(), message.getText());
		attributes.put("id", UUID.randomUUID().toString());
		queryParameters.put("messageProperties", attributes);

		if (!Optional.ofNullable(message.getMetadata()).orElse(Map.of()).isEmpty()) {
			statementBuilder.append("WITH msg\nCREATE (metadataNode:Metadata)\n");
			statementBuilder.append("SET metadataNode = $metadata\n");
			statementBuilder.append("CREATE (msg)-[:HAS_METADATA]->(metadataNode)\n");
			message.getMetadata().put(MessageAttributes.MESSAGE_TYPE.getValue(), message.getMetadata().get(MessageAttributes.MESSAGE_TYPE.getValue()).toString());
			queryParameters.put("metadata", message.getMetadata());
		}
		if(message instanceof AssistantMessage assistantMessage){
			if(assistantMessage.hasToolCalls()){
				statementBuilder.append("WITH msg\nFOREACH(tc in $toolCalls | CREATE (toolCall:ToolCall) SET toolCall = tc CREATE (msg)-[:HAS_TOOL_CALL]->(toolCall))\n");
				List<Map<String, Object>> toolCallMaps = new ArrayList<>();
				for(int i = 0; i<assistantMessage.getToolCalls().size(); i++){
					AssistantMessage.ToolCall tc = assistantMessage.getToolCalls().get(i);
					toolCallMaps.add(Map.of("id", tc.id(), "name", tc.name(), "arguments", tc.arguments(),
							"type", tc.type(), "idx", i));
				}
				queryParameters.put("toolCalls", toolCallMaps);
			}
		}

		if (message instanceof ToolResponseMessage toolResponseMessage) {
			List<ToolResponseMessage.ToolResponse> toolResponses = toolResponseMessage.getResponses();
			List<Map<String, String>> toolResponseMaps = new ArrayList<>();
			for (int i = 0; i < Optional.ofNullable(toolResponses).orElse(List.of()).size(); i++) {
				var toolResponse = toolResponses.get(i);
				Map<String, String> toolResponseMap = Map.of("id", toolResponse.id(),
						ToolResponseAttributes.NAME.getValue(), toolResponse.name(),
						ToolResponseAttributes.RESPONSE_DATA.getValue(), toolResponse.responseData(),
						ToolResponseAttributes.IDX.getValue(), Integer.toString(i));

				toolResponseMaps.add(toolResponseMap);
			}
			statementBuilder.append("WITH msg\nFOREACH(tr IN $toolResponses | CREATE (tm:ToolResponse)\n");
			statementBuilder.append("SET tm = tr MERGE (msg)-[:HAS_TOOL_RESPONSE]->(tm))\n");
			queryParameters.put("toolResponses", toolResponseMaps);
		}


		if (message instanceof MediaContent messageWithMedia && !messageWithMedia.getMedia().isEmpty()) {
				List<Map<String, Object>> mediaNodes = convertMediaToMap(messageWithMedia.getMedia());
				statementBuilder.append("WITH msg \nUNWIND $media AS m\nCREATE (media:Media) SET media = m\n");
				statementBuilder.append("WITH msg, media CREATE (msg)-[:HAS_MEDIA]->(media)\n");
				queryParameters.put("media", mediaNodes);

		}
		t.run(statementBuilder.toString(), queryParameters);
	}



	private List<Map<String, Object>> convertMediaToMap(List<Media> media) {
		return media.stream().map(m -> {
			Map<String, Object> mediaMap = new HashMap<>();
			mediaMap.put(MediaAttributes.ID.getValue(), m.getId());
			mediaMap.put(MediaAttributes.MIME_TYPE.getValue(), m.getMimeType().toString());
			mediaMap.put(MediaAttributes.NAME.getValue(), m.getName());
			mediaMap.put(MediaAttributes.DATA.getValue(), m.getData());
			return mediaMap;
		}

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
		String statementBuilder = "MATCH (s:Session {id:$conversationId})-[r:HAS_MESSAGE]->(m:Message)\n" +
				"WITH m ORDER BY m.idx DESC LIMIT $lastN\n" +
				"OPTIONAL MATCH (m)-[:HAS_METADATA]->(metadata:Metadata)\n" +
				"OPTIONAL MATCH (m)-[:HAS_MEDIA]->(media:Media)\n" +
				"OPTIONAL MATCH (m)-[:HAS_TOOL_RESPONSE]-(tr:ToolResponse) WITH m, metadata, media, tr ORDER BY tr.idx ASC\n" +
				"OPTIONAL MATCH (m)-[:HAS_TOOL_CALL]->(tc:ToolCall)\n WITH m, metadata, media, tr, tc ORDER BY tc.idx ASC\n" +
				"RETURN m, metadata, collect(tr) as toolResponses, collect(tc) as toolCalls, collect(media) as medias";
		Result res = this.driver.session().run(statementBuilder,
				Map.of("conversationId", conversationId, "lastN", lastN));
		return res.list(record -> {
			Map<String, Object> messageMap = record.get("m").asMap();
			String msgType = messageMap.get(MessageAttributes.MESSAGE_TYPE.getValue()).toString();
			Message message = null;
			if(msgType.equals(MessageType.USER.getValue())) {
				List<Media> mediaList = record.get("medias").asList(v -> {
					Map<String, Object> mediaMap = v.asMap();
					var mediaBuilder = Media.builder().name((String) mediaMap.get(MediaAttributes.NAME.getValue()))
							.id(Optional.ofNullable(mediaMap.get(MediaAttributes.ID.getValue())).map(Object::toString).orElse(null))
							.mimeType(MimeType.valueOf(mediaMap.get(MediaAttributes.MIME_TYPE.getValue()).toString()));
					if(mediaMap.get(MediaAttributes.DATA.getValue()) instanceof String stringData){
						try {
							mediaBuilder.data(URI.create(stringData).toURL());
						} catch (MalformedURLException e) {
							throw new IllegalArgumentException("Media data contains an invalid URL");
						}
					} else if(mediaMap.get(MediaAttributes.DATA.getValue()).getClass().isArray()) {
						mediaBuilder.data(mediaMap.get(MediaAttributes.DATA.getValue()));
					}
					return mediaBuilder.build();

				});
				message = new UserMessage(messageMap.get(MessageAttributes.TEXT_CONTENT.getValue()).toString(),
						mediaList, record.get("metadata").asMap(Map.of()));
			}
			if(msgType.equals(MessageType.ASSISTANT.getValue())){
				message = new AssistantMessage(messageMap.get(MessageAttributes.TEXT_CONTENT.getValue()).toString(),
						record.get("metadata").asMap(Map.of()), record.get("toolCalls").asList(v -> {
							var toolCallMap = v.asMap();
							return new AssistantMessage.ToolCall((String) toolCallMap.get("id"),
									(String) toolCallMap.get("type"), (String) toolCallMap.get("name"),
									(String) toolCallMap.get("arguments"));
				}));
			}
			if(message == null) {
				throw new IllegalArgumentException("%s messages are not supported".
						formatted(record.get(MessageAttributes.MESSAGE_TYPE.getValue()).asString()));
			}
			return message;
		});

	}

	@Override
	public void clear(String conversationId) {
		String statementBuilder = "MATCH (s:Session {id:$conversationId})-[r:HAS_MESSAGE]->(m:Message)\n" +
				"OPTIONAL MATCH (m)-[:HAS_METADATA]->(metadata:Metadata)\n" +
				"OPTIONAL MATCH (m)-[:HAS_MEDIA]->(media:Media)\n" +
				"OPTIONAL MATCH (m)-[:HAS_TOOL_RESPONSE]-(tr:ToolResponse)\n" +
				"DETACH DELETE m, metadata, media, tr";
		try(Transaction t = driver.session().beginTransaction()) {
			t.run(statementBuilder, Map.of("conversationId", conversationId));
			t.commit();
		}
	}

}
