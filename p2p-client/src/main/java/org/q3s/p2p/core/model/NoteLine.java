package org.q3s.p2p.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Linea inmutable de una nota en el modelo CRDT. Cada linea tiene un id unico
 * generado por el autor (memberId + counter) y se conserva como tombstone
 * aunque se borre, para que el orden relativo de inserciones y borrados
 * concurrentes converja en todos los peers.
 */
public record NoteLine(
		String lineId,
		String authorMemberId,
		String afterLineId,
		String text,
		Instant createdAt,
		boolean deleted) implements Serializable {

	public NoteLine withDeleted(boolean newDeleted) {
		if (this.deleted == newDeleted) return this;
		return new NoteLine(lineId, authorMemberId, afterLineId, text, createdAt, newDeleted);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof NoteLine other)) return false;
		return Objects.equals(lineId, other.lineId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(lineId);
	}
}
