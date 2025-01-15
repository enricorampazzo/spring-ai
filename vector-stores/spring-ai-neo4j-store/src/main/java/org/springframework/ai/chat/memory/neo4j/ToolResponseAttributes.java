package org.springframework.ai.chat.memory.neo4j;

import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * Keys to get/set values for {@link ToolResponseMessage.ToolResponse} object maps
 *
 * @author Enrico Rampazzo
 */
public enum ToolResponseAttributes {
	IDX("idx"), RESPONSE_DATA("responseData"), NAME("name"), ID("id");

	private final String value;

	ToolResponseAttributes(String value){
		this.value = value;
	}

	public String getValue() {
		return value;
	}
}
