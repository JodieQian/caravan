package com.ctrip.soa.caravan.protobuf.v2.customization.map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JacksonStdImpl;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.NullValueProvider;
import com.fasterxml.jackson.databind.deser.UnresolvedForwardReference;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.impl.ReadableObjectId.Referring;
import com.fasterxml.jackson.databind.deser.std.ContainerDeserializerBase;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.fasterxml.jackson.databind.util.ClassUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * 1. Lessen the generic parameter from Collection&lt;Object&gt; to Object so that
 * we can 'replace' the deserialized Collection instance to Map instance.
 *
 * 2. Change vt according to current property in createContextual() and
 * pass vt to withResolved() so that we can get the current type in MapDeserializer.withResolved().
 */
@JacksonStdImpl
public class CustomCollectionDeserializer
    extends ContainerDeserializerBase<Object>
    implements ContextualDeserializer {

  private static final long serialVersionUID = -1L; // since 2.5

  // // Configuration

  /**
   * Value deserializer.
   */
  protected final JsonDeserializer<Object> _valueDeserializer;

  /**
   * If element instances have polymorphic type information, this
   * is the type deserializer that can handle it
   */
  protected final TypeDeserializer _valueTypeDeserializer;

  // // Instance construction settings:

  protected final ValueInstantiator _valueInstantiator;

  /**
   * Deserializer that is used iff delegate-based creator is
   * to be used for deserializing from JSON Object.
   */
  protected final JsonDeserializer<Object> _delegateDeserializer;

  // NOTE: no PropertyBasedCreator, as JSON Arrays have no properties

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

  /**
   * Constructor for context-free instances, where we do not yet know
   * which property is using this deserializer.
   */
  public CustomCollectionDeserializer(JavaType collectionType,
      JsonDeserializer<Object> valueDeser,
      TypeDeserializer valueTypeDeser, ValueInstantiator valueInstantiator) {
    this(collectionType, valueDeser, valueTypeDeser, valueInstantiator, null, null, null);
  }

  /**
   * Constructor used when creating contextualized instances.
   *
   * @since 2.9
   */
  protected CustomCollectionDeserializer(JavaType collectionType,
      JsonDeserializer<Object> valueDeser, TypeDeserializer valueTypeDeser,
      ValueInstantiator valueInstantiator, JsonDeserializer<Object> delegateDeser,
      NullValueProvider nuller, Boolean unwrapSingle) {
    super(collectionType, nuller, unwrapSingle);
    _valueDeserializer = valueDeser;
    _valueTypeDeserializer = valueTypeDeser;
    _valueInstantiator = valueInstantiator;
    _delegateDeserializer = delegateDeser;
  }

  /**
   * Copy-constructor that can be used by sub-classes to allow
   * copy-on-write styling copying of settings of an existing instance.
   */
  protected CustomCollectionDeserializer(CustomCollectionDeserializer src) {
    super(src);
    _valueDeserializer = src._valueDeserializer;
    _valueTypeDeserializer = src._valueTypeDeserializer;
    _valueInstantiator = src._valueInstantiator;
    _delegateDeserializer = src._delegateDeserializer;
  }

  /**
   * Fluent-factory method call to construct contextual instance.
   *
   * @since 2.9
   */
  @SuppressWarnings("unchecked")
  protected CustomCollectionDeserializer withResolved(JavaType vt, JsonDeserializer<?> dd,
      JsonDeserializer<?> vd, TypeDeserializer vtd,
      NullValueProvider nuller, Boolean unwrapSingle) {
    return new CustomCollectionDeserializer(_containerType,
        (JsonDeserializer<Object>) vd, vtd,
        _valueInstantiator, (JsonDeserializer<Object>) dd,
        nuller, unwrapSingle);
  }

  // Important: do NOT cache if polymorphic values
  @Override // since 2.5
  public boolean isCachable() {
    // 26-Mar-2015, tatu: As per [databind#735], need to be careful
    return (_valueDeserializer == null)
        && (_valueTypeDeserializer == null)
        && (_delegateDeserializer == null)
        ;
  }

    /*
    /**********************************************************
    /* Validation, post-processing (ResolvableDeserializer)
    /**********************************************************
     */

  /**
   * Method called to finalize setup of this deserializer,
   * when it is known for which property deserializer is needed
   * for.
   */
  @Override
  public CustomCollectionDeserializer createContextual(DeserializationContext ctxt,
      BeanProperty property) throws JsonMappingException {
    MapType mapType = (MapType) property.getType();

    // May need to resolve types for delegate-based creators:
    JsonDeserializer<Object> delegateDeser = null;
    if (_valueInstantiator != null) {
      if (_valueInstantiator.canCreateUsingDelegate()) {
        JavaType delegateType = _valueInstantiator.getDelegateType(ctxt.getConfig());
        if (delegateType == null) {
          ctxt.reportBadDefinition(_containerType, String.format(
              "Invalid delegate-creator definition for %s: value instantiator (%s) returned true for 'canCreateUsingDelegate()', but null for 'getDelegateType()'",
              _containerType,
              _valueInstantiator.getClass().getName()));
        }
        delegateDeser = findDeserializer(ctxt, delegateType, property);
      } else if (_valueInstantiator.canCreateUsingArrayDelegate()) {
        JavaType delegateType = _valueInstantiator.getArrayDelegateType(ctxt.getConfig());
        if (delegateType == null) {
          ctxt.reportBadDefinition(_containerType, String.format(
              "Invalid delegate-creator definition for %s: value instantiator (%s) returned true for 'canCreateUsingArrayDelegate()', but null for 'getArrayDelegateType()'",
              _containerType,
              _valueInstantiator.getClass().getName()));
        }
        delegateDeser = findDeserializer(ctxt, delegateType, property);
      }
    }
    // [databind#1043]: allow per-property allow-wrapping of single overrides:
    // 11-Dec-2015, tatu: Should we pass basic `Collection.class`, or more refined? Mostly
    //   comes down to "List vs Collection" I suppose... for now, pass Collection
    Boolean unwrapSingle = findFormatFeature(ctxt, property, Collection.class,
        JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    // also, often value deserializer is resolved here:
    JsonDeserializer<?> valueDeser = _valueDeserializer;

    // May have a content converter
    valueDeser = findConvertingContentDeserializer(ctxt, property, valueDeser);
    final JavaType vt = SimpleType.constructUnsafe(DynamicClassFactory.INSTANCE.fetchOrCreatePairClass(mapType));
    if (valueDeser == null) {
      valueDeser = ctxt.findContextualValueDeserializer(vt, property);
    } else { // if directly assigned, probably not yet contextual, so:
      valueDeser = ctxt.handleSecondaryContextualization(valueDeser, property, vt);
    }
    // and finally, type deserializer needs context as well
    TypeDeserializer valueTypeDeser = _valueTypeDeserializer;
    if (valueTypeDeser != null) {
      valueTypeDeser = valueTypeDeser.forProperty(property);
    }
    NullValueProvider nuller = findContentNullProvider(ctxt, property, valueDeser);
    if ((unwrapSingle != _unwrapSingle)
        || (nuller != _nullProvider)
        || (delegateDeser != _delegateDeserializer)
        || (valueDeser != _valueDeserializer)
        || (valueTypeDeser != _valueTypeDeserializer)
        ) {
      return withResolved(vt, delegateDeser, valueDeser, valueTypeDeser,
          nuller, unwrapSingle);
    }
    return this;
  }

    /*
    /**********************************************************
    /* ContainerDeserializerBase API
    /**********************************************************
     */

  @Override
  public JsonDeserializer<Object> getContentDeserializer() {
    return _valueDeserializer;
  }

  @Override
  public ValueInstantiator getValueInstantiator() {
    return _valueInstantiator;
  }

    /*
    /**********************************************************
    /* JsonDeserializer API
    /**********************************************************
     */

  @SuppressWarnings("unchecked")
  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    if (_delegateDeserializer != null) {
      return (Collection<Object>) _valueInstantiator.createUsingDelegate(ctxt,
          _delegateDeserializer.deserialize(p, ctxt));
    }
    // Empty String may be ok; bit tricky to check, however, since
    // there is also possibility of "auto-wrapping" of single-element arrays.
    // Hence we only accept empty String here.
    if (p.hasToken(JsonToken.VALUE_STRING)) {
      String str = p.getText();
      if (str.length() == 0) {
        return (Collection<Object>) _valueInstantiator.createFromString(ctxt, str);
      }
    }
    return deserialize(p, ctxt, createDefaultInstance(ctxt));
  }

  /**
   * @since 2.9
   */
  @SuppressWarnings("unchecked")
  protected Collection<Object> createDefaultInstance(DeserializationContext ctxt)
      throws IOException {
    return (Collection<Object>) _valueInstantiator.createUsingDefault(ctxt);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt,
      Object result)
      throws IOException {
    // Ok: must point to START_ARRAY (or equivalent)
    if (!p.isExpectedStartArrayToken()) {
      return handleNonArray(p, ctxt, (Collection<Object>) result);
    }
    // [databind#631]: Assign current value, to be accessible by custom serializers
    p.setCurrentValue(result);

    JsonDeserializer<Object> valueDes = _valueDeserializer;
    final TypeDeserializer typeDeser = _valueTypeDeserializer;
    CollectionReferringAccumulator referringAccumulator =
        (valueDes.getObjectIdReader() == null) ? null :
            new CollectionReferringAccumulator(_containerType.getContentType().getRawClass(), (Collection<Object>) result);

    JsonToken t;
    while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
      try {
        Object value;
        if (t == JsonToken.VALUE_NULL) {
          if (_skipNullValues) {
            continue;
          }
          value = _nullProvider.getNullValue(ctxt);
        } else if (typeDeser == null) {
          value = valueDes.deserialize(p, ctxt);
        } else {
          value = valueDes.deserializeWithType(p, ctxt, typeDeser);
        }
        if (referringAccumulator != null) {
          referringAccumulator.add(value);
        } else {
          ((Collection<Object>) result).add(value);
        }
      } catch (UnresolvedForwardReference reference) {
        if (referringAccumulator == null) {
          throw JsonMappingException
              .from(p, "Unresolved forward reference but no identity info", reference);
        }
        Referring ref = referringAccumulator.handleUnresolvedReference(reference);
        reference.getRoid().appendReferring(ref);
      } catch (Exception e) {
        boolean wrap = (ctxt == null) || ctxt.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS);
        if (!wrap) {
          ClassUtil.throwIfRTE(e);
        }
        throw JsonMappingException.wrapWithPath(e, result, ((Collection<Object>) result).size());
      }
    }
    return result;
  }

  @Override
  public Object deserializeWithType(JsonParser p, DeserializationContext ctxt,
      TypeDeserializer typeDeserializer)
      throws IOException {
    // In future could check current token... for now this should be enough:
    return typeDeserializer.deserializeTypedFromArray(p, ctxt);
  }

  /**
   * Helper method called when current token is no START_ARRAY. Will either
   * throw an exception, or try to handle value as if member of implicit
   * array, depending on configuration.
   */
  @SuppressWarnings("unchecked")
  protected final Collection<Object> handleNonArray(JsonParser p, DeserializationContext ctxt,
      Collection<Object> result)
      throws IOException {
    // Implicit arrays from single values?
    boolean canWrap = (_unwrapSingle == Boolean.TRUE) ||
        ((_unwrapSingle == null) &&
            ctxt.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY));
    if (!canWrap) {
      return (Collection<Object>) ctxt.handleUnexpectedToken(_containerType.getRawClass(), p);
    }
    JsonDeserializer<Object> valueDes = _valueDeserializer;
    final TypeDeserializer typeDeser = _valueTypeDeserializer;
    JsonToken t = p.getCurrentToken();

    Object value;

    try {
      if (t == JsonToken.VALUE_NULL) {
        // 03-Feb-2017, tatu: Hmmh. I wonder... let's try skipping here, too
        if (_skipNullValues) {
          return result;
        }
        value = _nullProvider.getNullValue(ctxt);
      } else if (typeDeser == null) {
        value = valueDes.deserialize(p, ctxt);
      } else {
        value = valueDes.deserializeWithType(p, ctxt, typeDeser);
      }
    } catch (Exception e) {
      // note: pass Object.class, not Object[].class, as we need element type for error info
      throw JsonMappingException.wrapWithPath(e, Object.class, result.size());
    }
    result.add(value);
    return result;
  }

  public final static class CollectionReferringAccumulator {

    private final Class<?> _elementType;
    private final Collection<Object> _result;

    /**
     * A list of {@link CollectionReferring} to maintain ordering.
     */
    private List<CollectionReferring> _accumulator = new ArrayList<CollectionReferring>();

    public CollectionReferringAccumulator(Class<?> elementType, Collection<Object> result) {
      _elementType = elementType;
      _result = result;
    }

    public void add(Object value) {
      if (_accumulator.isEmpty()) {
        _result.add(value);
      } else {
        CollectionReferring ref = _accumulator.get(_accumulator.size() - 1);
        ref.next.add(value);
      }
    }

    public Referring handleUnresolvedReference(UnresolvedForwardReference reference) {
      CollectionReferring id = new CollectionReferring(this, reference, _elementType);
      _accumulator.add(id);
      return id;
    }

    public void resolveForwardReference(Object id, Object value) throws IOException {
      Iterator<CollectionReferring> iterator = _accumulator.iterator();
      // Resolve ordering after resolution of an id. This mean either:
      // 1- adding to the result collection in case of the first unresolved id.
      // 2- merge the content of the resolved id with its previous unresolved id.
      Collection<Object> previous = _result;
      while (iterator.hasNext()) {
        CollectionReferring ref = iterator.next();
        if (ref.hasId(id)) {
          iterator.remove();
          previous.add(value);
          previous.addAll(ref.next);
          return;
        }
        previous = ref.next;
      }

      throw new IllegalArgumentException("Trying to resolve a forward reference with id [" + id
          + "] that wasn't previously seen as unresolved.");
    }
  }

  /**
   * Helper class to maintain processing order of value. The resolved
   * object associated with {@link #_id} comes before the values in
   * {@link #next}.
   */
  private final static class CollectionReferring extends Referring {

    private final CollectionReferringAccumulator _parent;
    public final List<Object> next = new ArrayList<Object>();

    CollectionReferring(CollectionReferringAccumulator parent,
        UnresolvedForwardReference reference, Class<?> contentType) {
      super(reference, contentType);
      _parent = parent;
    }

    @Override
    public void handleResolvedForwardReference(Object id, Object value) throws IOException {
      _parent.resolveForwardReference(id, value);
    }
  }
}