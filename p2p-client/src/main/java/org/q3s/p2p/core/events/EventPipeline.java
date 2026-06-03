package org.q3s.p2p.core.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.ports.AuthProvider;
import org.q3s.p2p.ports.EventStore;

/**
 * Unico punto de entrada para eventos en el core. Centraliza stamping, validacion,
 * persistencia y notificacion a observadores. Los callers deben preferir este API
 * sobre llamadas directas a EventService / EventStore.
 */
public interface EventPipeline {

	AppendResult appendLocal(Event draft);

	boolean acceptRemote(Event event);

	int acceptRemoteBatch(List<Event> events);

	void onEventStored(Consumer<Event> listener);

	static EventPipeline create(EventStore store, AuthProvider auth) {
		return new DefaultEventPipeline(store, auth);
	}

	enum AppendStatus { OK, REJECTED_VALIDATION, DUPLICATE, INVALID }

	final class AppendResult {
		private final Event event;
		private final AppendStatus status;
		private final String reason;

		private AppendResult(Event event, AppendStatus status, String reason) {
			this.event = event;
			this.status = status;
			this.reason = reason;
		}

		public static AppendResult ok(Event event) { return new AppendResult(event, AppendStatus.OK, null); }
		public static AppendResult rejected(Event event, String reason) { return new AppendResult(event, AppendStatus.REJECTED_VALIDATION, reason); }
		public static AppendResult duplicate(Event event) { return new AppendResult(event, AppendStatus.DUPLICATE, "eventId ya existe"); }
		public static AppendResult invalid(Event event, String reason) { return new AppendResult(event, AppendStatus.INVALID, reason); }

		public Event event() { return event; }
		public AppendStatus status() { return status; }
		public String reason() { return reason; }
		public boolean accepted() { return status == AppendStatus.OK; }
	}

	final class DefaultEventPipeline implements EventPipeline {
		private final EventStore store;
		private final AuthProvider auth;
		private final List<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();
		private volatile Member currentAuthor;

		DefaultEventPipeline(EventStore store, AuthProvider auth) {
			this.store = store;
			this.auth = auth;
		}

		public void bindCurrentAuthor(Member author) {
			this.currentAuthor = author;
		}

		@Override
		public AppendResult appendLocal(Event draft) {
			if (draft == null) return AppendResult.invalid(null, "draft null");
			if (draft.eventId() == null || draft.eventId().isBlank()) return AppendResult.invalid(draft, "eventId vacio");
			if (store.hasEvent(draft.eventId())) return AppendResult.duplicate(draft);
			Event stamped = currentAuthor != null ? auth.stampEvent(draft, currentAuthor) : draft;
			store.append(stamped);
			notify(stamped);
			return AppendResult.ok(stamped);
		}

		@Override
		public boolean acceptRemote(Event event) {
			if (event == null || event.eventId() == null) return false;
			if (store.hasEvent(event.eventId())) return false;
			if (event.isEphemeral()) {
				notify(event);
				return true;
			}
			if (!new EventValidator(store).isAcceptable(event)) return false;
			store.append(event);
			notify(event);
			return true;
		}

		@Override
		public int acceptRemoteBatch(List<Event> events) {
			if (events == null) return 0;
			int accepted = 0;
			for (Event event : events) {
				if (acceptRemote(event)) accepted++;
			}
			return accepted;
		}

		@Override
		public void onEventStored(Consumer<Event> listener) {
			if (listener != null) listeners.add(listener);
		}

		private void notify(Event event) {
			for (Consumer<Event> listener : listeners) {
				try { listener.accept(event); } catch (Exception ignored) { ignored.getMessage(); }
			}
		}
	}
}
