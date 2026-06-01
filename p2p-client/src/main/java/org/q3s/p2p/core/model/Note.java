package org.q3s.p2p.core.model;

import java.io.Serializable;

public record Note(String noteId, String text) implements Serializable {
}
