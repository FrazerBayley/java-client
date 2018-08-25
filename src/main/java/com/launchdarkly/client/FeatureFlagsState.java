package com.launchdarkly.client;

import com.google.common.base.Objects;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A snapshot of the state of all feature flags with regard to a specific user, generated by
 * calling {@link LDClientInterface#allFlagsState(LDUser)}.
 * <p>
 * Serializing this object to JSON using Gson will produce the appropriate data structure for
 * bootstrapping the LaunchDarkly JavaScript client.
 * 
 * @since 4.3.0
 */
@JsonAdapter(FeatureFlagsState.JsonSerialization.class)
public class FeatureFlagsState {
  private static final Gson gson = new Gson();
  
  private final Map<String, JsonElement> flagValues;
  private final Map<String, FlagMetadata> flagMetadata;
  private final boolean valid;
    
  static class FlagMetadata {
    final Integer variation;
    final EvaluationReason reason;
    final int version;
    final boolean trackEvents;
    final Long debugEventsUntilDate;
    
    FlagMetadata(Integer variation, EvaluationReason reason, int version, boolean trackEvents,
        Long debugEventsUntilDate) {
      this.variation = variation;
      this.reason = reason;
      this.version = version;
      this.trackEvents = trackEvents;
      this.debugEventsUntilDate = debugEventsUntilDate;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof FlagMetadata) {
        FlagMetadata o = (FlagMetadata)other;
        return Objects.equal(variation, o.variation) &&
            version == o.version &&
            trackEvents == o.trackEvents &&
            Objects.equal(debugEventsUntilDate, o.debugEventsUntilDate);
      }
      return false;
    }
    
    @Override
    public int hashCode() {
      return Objects.hashCode(variation, version, trackEvents, debugEventsUntilDate);
    }
  }
  
  private FeatureFlagsState(Map<String, JsonElement> flagValues,
      Map<String, FlagMetadata> flagMetadata, boolean valid) {
    this.flagValues = Collections.unmodifiableMap(flagValues);
    this.flagMetadata = Collections.unmodifiableMap(flagMetadata);
    this.valid = valid;
  }
  
  /**
   * Returns true if this object contains a valid snapshot of feature flag state, or false if the
   * state could not be computed (for instance, because the client was offline or there was no user).
   * @return true if the state is valid
   */
  public boolean isValid() {
    return valid;
  }
  
  /**
   * Returns the value of an individual feature flag at the time the state was recorded.
   * @param key the feature flag key
   * @return the flag's JSON value; null if the flag returned the default value, or if there was no such flag
   */
  public JsonElement getFlagValue(String key) {
    return flagValues.get(key);
  }

  /**
   * Returns the evaluation reason for an individual feature flag at the time the state was recorded.
   * @param key the feature flag key
   * @return an {@link EvaluationReason}; null if reasons were not recorded, or if there was no such flag
   */
  public EvaluationReason getFlagReason(String key) {
    FlagMetadata data = flagMetadata.get(key);
    return data == null ? null : data.reason;
  }
  
  /**
   * Returns a map of flag keys to flag values. If a flag would have evaluated to the default value,
   * its value will be null.
   * <p>
   * Do not use this method if you are passing data to the front end to "bootstrap" the JavaScript client.
   * Instead, serialize the FeatureFlagsState object to JSON using {@code Gson.toJson()} or {@code Gson.toJsonTree()}.
   * @return an immutable map of flag keys to JSON values
   */
  public Map<String, JsonElement> toValuesMap() {
    return flagValues;
  }
  
  @Override
  public boolean equals(Object other) {
    if (other instanceof FeatureFlagsState) {
      FeatureFlagsState o = (FeatureFlagsState)other;
      return flagValues.equals(o.flagValues) &&
          flagMetadata.equals(o.flagMetadata) &&
          valid == o.valid;
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    return Objects.hashCode(flagValues, flagMetadata, valid);
  }
  
  static class Builder {
    private Map<String, JsonElement> flagValues = new HashMap<>();
    private Map<String, FlagMetadata> flagMetadata = new HashMap<>();
    private final boolean saveReasons;
    private boolean valid = true;

    Builder(FlagsStateOption... options) {
      saveReasons = FlagsStateOption.hasOption(options, FlagsStateOption.WITH_REASONS);
    }
    
    Builder valid(boolean valid) {
      this.valid = valid;
      return this;
    }
    
    Builder addFlag(FeatureFlag flag, EvaluationDetail<JsonElement> eval) {
      flagValues.put(flag.getKey(), eval.getValue());
      FlagMetadata data = new FlagMetadata(eval.getVariationIndex(),
          saveReasons ? eval.getReason() : null,
          flag.getVersion(), flag.isTrackEvents(), flag.getDebugEventsUntilDate());
      flagMetadata.put(flag.getKey(), data);
      return this;
    }
    
    FeatureFlagsState build() {
      return new FeatureFlagsState(flagValues, flagMetadata, valid);
    }
  }
  
  static class JsonSerialization extends TypeAdapter<FeatureFlagsState> {
    @Override
    public void write(JsonWriter out, FeatureFlagsState state) throws IOException {
      out.beginObject();
      for (Map.Entry<String, JsonElement> entry: state.flagValues.entrySet()) {
        out.name(entry.getKey());
        gson.toJson(entry.getValue(), out);
      }
      out.name("$flagsState");
      gson.toJson(state.flagMetadata, Map.class, out);
      out.name("$valid");
      out.value(state.valid);
      out.endObject();
    }

    // There isn't really a use case for deserializing this, but we have to implement it
    @Override
    public FeatureFlagsState read(JsonReader in) throws IOException {
      Map<String, JsonElement> flagValues = new HashMap<>();
      Map<String, FlagMetadata> flagMetadata = new HashMap<>();
      boolean valid = true;
      in.beginObject();
      while (in.hasNext()) {
        String name = in.nextName();
        if (name.equals("$flagsState")) {
          in.beginObject();
          while (in.hasNext()) {
            String metaName = in.nextName();
            FlagMetadata meta = gson.fromJson(in, FlagMetadata.class);
            flagMetadata.put(metaName, meta);
          }
          in.endObject();
        } else if (name.equals("$valid")) {
          valid = in.nextBoolean();
        } else {
          JsonElement value = gson.fromJson(in, JsonElement.class);
          flagValues.put(name, value);
        }
      }
      in.endObject();
      return new FeatureFlagsState(flagValues, flagMetadata, valid);
    }
  }
}
