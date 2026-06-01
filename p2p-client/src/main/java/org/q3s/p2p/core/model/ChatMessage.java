package org.q3s.p2p.core.model;

import java.io.Serializable;

public record ChatMessage(String messageId, String authorMemberId, String text) implements Serializable {
}
