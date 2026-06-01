package org.q3s.p2p.core.model;

import java.io.Serializable;
import java.util.List;

public record WhiteboardStroke(String strokeId, String authorMemberId, List<int[]> points, String color, int width) implements Serializable {
}
