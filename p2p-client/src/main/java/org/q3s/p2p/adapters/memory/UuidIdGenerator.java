package org.q3s.p2p.adapters.memory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.q3s.p2p.ports.IdGenerator;

public class UuidIdGenerator implements IdGenerator {
	@Override
	public String newId(String prefix) {
		String value = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		if ("ws".equals(prefix)) {
			return "ws_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "_" + value.substring(0, 6);
		}
		return prefix + "_" + value;
	}
}
