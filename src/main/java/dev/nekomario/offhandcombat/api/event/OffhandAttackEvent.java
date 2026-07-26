package dev.nekomario.offhandcombat.api.event;

import dev.nekomario.offhandcombat.api.OffhandAttackContext;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class OffhandAttackEvent extends Event {
    private final OffhandAttackContext context;

    protected OffhandAttackEvent(OffhandAttackContext context) {
        this.context = context;
    }

    public OffhandAttackContext context() {
        return context;
    }

    public static final class Before extends OffhandAttackEvent implements ICancellableEvent {
        public Before(OffhandAttackContext context) {
            super(context);
        }
    }

    public static final class After extends OffhandAttackEvent {
        private final OffhandAttackResult result;

        public After(OffhandAttackContext context, OffhandAttackResult result) {
            super(context);
            this.result = result;
        }

        public OffhandAttackResult result() {
            return result;
        }
    }
}
