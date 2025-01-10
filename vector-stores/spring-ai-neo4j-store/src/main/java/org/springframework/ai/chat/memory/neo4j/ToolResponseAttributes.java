package org.springframework.ai.chat.memory.neo4j;

import org.springframework.ai.chat.messages.ToolResponseMessage;

public enum ToolResponseAttributes {
	IDX("idx"), RESPONSE_DATA("responseData"), NAME("name");

	private final String value;

	ToolResponseAttributes(String value){
		this.value = value;
	}

	public String getValue() {
		return value;
	}
}
