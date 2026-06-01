package org.q3s.p2p.core.model;

import java.io.Serializable;

public record Workspace(String workspaceId, String name, int requiredApprovals, String authMode) implements Serializable {
}
