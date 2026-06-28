package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ObjectiveDefinition {
    private final String id;
    private final String displayName;
    private final int completionTicks;
    private final List<String> requirements;
    private final VisibilityPolicy visibility;
    private final ControlPolicy controlPolicy;
    private final CarryPolicy carryPolicy;

    private ObjectiveDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.completionTicks = builder.completionTicks;
        this.requirements = List.copyOf(builder.requirements);
        this.visibility = builder.visibility;
        this.controlPolicy = builder.controlPolicy;
        this.carryPolicy = builder.carryPolicy;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int completionTicks() {
        return completionTicks;
    }

    public List<String> requirements() {
        return requirements;
    }

    public VisibilityPolicy visibility() {
        return visibility;
    }

    public ControlPolicy controlPolicy() {
        return controlPolicy;
    }

    public CarryPolicy carryPolicy() {
        return carryPolicy;
    }

    public static final class Builder {
        private final String id;
        private String displayName;
        private int completionTicks;
        private final List<String> requirements = new ArrayList<>();
        private VisibilityPolicy visibility = VisibilityPolicy.global();
        private ControlPolicy controlPolicy = ControlPolicy.none();
        private CarryPolicy carryPolicy = CarryPolicy.none();

        private Builder(String id) {
            this.id = requireId(id);
            this.displayName = id;
        }

        public Builder displayName(String displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        public Builder completionTicks(int completionTicks) {
            if (completionTicks < 0) {
                throw new IllegalArgumentException("completionTicks must be >= 0");
            }
            this.completionTicks = completionTicks;
            return this;
        }

        public Builder requires(String objectiveId) {
            this.requirements.add(requireId(objectiveId));
            return this;
        }

        public Builder visibility(VisibilityPolicy visibility) {
            this.visibility = Objects.requireNonNull(visibility, "visibility");
            return this;
        }

        public Builder controlPolicy(ControlPolicy controlPolicy) {
            this.controlPolicy = Objects.requireNonNull(controlPolicy, "controlPolicy");
            return this;
        }

        public Builder carryPolicy(CarryPolicy carryPolicy) {
            this.carryPolicy = Objects.requireNonNull(carryPolicy, "carryPolicy");
            return this;
        }

        public ObjectiveDefinition build() {
            return new ObjectiveDefinition(this);
        }

        private static String requireId(String id) {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            return id;
        }
    }
}
