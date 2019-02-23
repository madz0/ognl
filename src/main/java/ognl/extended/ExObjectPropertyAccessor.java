/*
 * Decompiled with CFR 0.139.
 */
package ognl.extended;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

import static ognl.extended.Config.*;

import ognl.*;
import ognl.internal.extended.ArraySourceContainer;
import ognl.internal.extended.MutableInt;

public class ExObjectPropertyAccessor extends ObjectPropertyAccessor implements PropertyAccessor {
    /*
     * Enabled aggressive block sorting Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Object getProperty(Map context, Object target, Object name) throws OgnlException {
        try {
            OgnlContext ognlContext = (OgnlContext) context;
            int level = this.incIndex(context);
            if (level == 1 && this.isFirstAlwaysIgnored(context)
                    && target.getClass().isAssignableFrom(ognlContext.getRoot().getClass())) {
                return target;
            }
            if (!this.hasGetProperty(context, target, name)) {
                if (level == 1 && this.isFirstUnknownIgnored(context)
                        && target.getClass().isAssignableFrom(ognlContext.getRoot().getClass())) {
                    return target;
                }
                if (!this.isUnknownIsLiteral(context)) {
                    StringBuffer sb = new StringBuffer();
                    sb.append("Could not find property ").append(name).append("  of ").append(target.getClass());
                    throw new OgnlException(sb.toString());
                }
                if (name == null)
                    return null;
                String string = name.toString();
                return string;
            }
            if (!this.isSetChain(context)) {
                return super.getProperty(context, target, name);
            }
            if (!this.isNullInited(context)) {
                return super.getProperty(context, target, name);
            }
            Object value = this.getPossibleProperty(context, target, (String) name);
            Type[] generics = this.getPossibleSetGenericTypes(ognlContext, target, (String) name);
            if (generics != null) {
                this.checkSetGenericTypes(context, generics, level);
            }
            Class cls = this.getPropertyClass(ognlContext, target, name);
            Class<?> componentType = null;
            if (cls == null || cls == Void.TYPE) {
                if (!this.isUnknownInited(context)) {
                    StringBuffer sb = new StringBuffer();
                    sb.append("Could not determine type of the ").append(name).append(" getter of ").append(target.getClass());
                    throw new OgnlException(sb.toString());
                }
                cls = Object.class;
            } else if (cls.isArray()) {
                componentType = cls.getComponentType();
                this.keepArraySource(ognlContext, target, (String) name, level);
            }
            if (value == null) {
                value = this.createProperObject(ognlContext, cls, componentType);
                if (this.setPossibleProperty(context, target, (String) name, value) == OgnlRuntime.NotFound) {
                    StringBuffer sb = new StringBuffer();
                    sb.append("Could not set value ").append(value).append(" with property ").append(name).append(" to ")
                            .append(target.getClass());
                    throw new OgnlException(sb.toString());
                }
            }

            return ((ObjectConstructor)context.get(OBJECT_CONSTRUCTOR_KEY)).processObject(
                    ognlContext, OgnlRuntime.getPropertyDescriptor(target.getClass(), (String) name),
                    target, value, (Map<String, Ognl.MyNode>) context.get(Config.NEXT_CHAIN));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void setProperty(Map context, Object target, Object name, Object value) throws OgnlException {
        OgnlContext ognlContext = (OgnlContext) context;
        int level = this.incIndex(context);
        if (level == 1 && this.isFirstAlwaysIgnored(context)
                && target.getClass().isAssignableFrom(ognlContext.getRoot().getClass())) {
            return;
        }
        if (this.setPossibleProperty(context, target, (String) name, value) == OgnlRuntime.NotFound) {
            if (level == 1 && this.isFirstUnknownIgnored(context)
                    && target.getClass().isAssignableFrom(ognlContext.getRoot().getClass())) {
                return;
            }
            StringBuffer sb = new StringBuffer();
            sb.append("Could not set value ").append(value).append(" with property ").append(name).append(" to ")
                    .append(target.getClass());
            throw new OgnlException(sb.toString());
        }
    }

    public int incIndex(Map context) {
        return ((MutableInt) context.get(CURRENT_INDEX_KEY)).incGet();
    }

    public int decIndex(Map context) {
        return ((MutableInt) context.get(CURRENT_INDEX_KEY)).decGet();
    }

    public boolean isSetChain(Map context) {
        return true;
    }

    public boolean isNullInited(Map context) {
        return true;
    }

    public boolean isExpanded(Map context) {
        return true;
    }

    public boolean isUnknownInited(Map context) {
        return true;
    }

    public boolean isFirstUnknownIgnored(Map context) {
        return true;
    }

    public boolean isFirstAlwaysIgnored(Map context) {
        return false;
    }

    public boolean isUnknownIsLiteral(Map context) {
        return true;
    }

    public void checkSetGenericTypes(Map context, Type[] genericTypes, int level) {
        if (genericTypes.length == 0) {
            return;
        }
        if (genericTypes[0] instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) genericTypes[0];
            genericTypes = ptype.getActualTypeArguments();
            if (genericTypes == null || genericTypes.length == 0) {
                return;
            }
            StringBuffer key = new StringBuffer();
            key.append(GENERIC_PREFIX_KEY).append(String.valueOf(level + 1));
            context.put(key.toString(), (Object) genericTypes);
        } else if (genericTypes[0] instanceof GenericArrayType) {
            GenericArrayType genericArrayType = (GenericArrayType) genericTypes[0];
            ParameterizedType ptype = null;
            Type tmp_type = genericArrayType.getGenericComponentType();
            do {
                if (tmp_type instanceof GenericArrayType) {
                    ++level;
                    tmp_type = ((GenericArrayType) tmp_type).getGenericComponentType();
                    continue;
                }
                if (tmp_type instanceof ParameterizedType)
                    break;
            } while (true);
            ptype = (ParameterizedType) tmp_type;
            genericTypes = ptype.getActualTypeArguments();
            if (genericTypes == null || genericTypes.length == 0) {
                return;
            }
            StringBuffer key = new StringBuffer();
            key.append(GENERIC_PREFIX_KEY).append(String.valueOf(level + 2));
            context.put(key.toString(), (Object) genericTypes);
        }
    }

    public Object createProperObject(OgnlContext context, Class<?> cls, Class<?> componentType)
            throws InstantiationException, IllegalAccessException {
        return ((ObjectConstructor) context.get(OBJECT_CONSTRUCTOR_KEY)).createObject(cls, componentType);
    }

    public void keepArraySource(OgnlContext context, Object target, String propertyName, int level) {
        StringBuffer key = new StringBuffer();
        key.append(ARRAR_SOURCE_PREFIX_KEY).append(String.valueOf(level + 1));
        ArraySourceContainer a = new ArraySourceContainer();
        a.setSetterName(propertyName);
        a.setTarget(target);
        context.put(key.toString(), a);
    }

    public Type[] getPossibleSetGenericTypes(OgnlContext context, Object target, String name) throws Exception {
        Type g;
        Method setMethod = OgnlRuntime.getSetMethod(context, target.getClass(), name);
        if (setMethod != null) {
            return setMethod.getGenericParameterTypes();
        }
        Field setField = OgnlRuntime.getField(target.getClass(), name);
        if (setField != null && (g = setField.getGenericType()) != null) {
            Type[] generics = new Type[]{g};
            return generics;
        }
        return null;
    }

    public void shiftGenericParameters(OgnlContext context, int level) {
        StringBuffer key = new StringBuffer();
        key.append(GENERIC_PREFIX_KEY).append(String.valueOf(level));
        Type[] genericParameterTypes = (Type[]) context.get(key.toString());
        if (genericParameterTypes != null && genericParameterTypes.length > 0) {
            key = new StringBuffer();
            key.append(GENERIC_PREFIX_KEY).append(String.valueOf(level + 1));
            context.put(key.toString(), (Object) genericParameterTypes);
        }
    }

    public int getGenericArgumentsCount() {
        return 0;
    }

    public Object getParameterizedType(OgnlContext context, int level, int paramIndex) {
        int next_classes_len;
        if (this.getGenericArgumentsCount() < 1 || paramIndex < 0) {
            return null;
        }
        StringBuffer key = new StringBuffer().append(GENERIC_PREFIX_KEY).append(String.valueOf(level));
        Type[] genericParameterTypes = (Type[]) context.get(key.toString());
        if (genericParameterTypes == null || genericParameterTypes.length < this.getGenericArgumentsCount()
                || genericParameterTypes.length <= paramIndex) {
            return null;
        }
        if (genericParameterTypes instanceof Class[]
                && (next_classes_len = genericParameterTypes.length - this.getGenericArgumentsCount()) > 0) {
            Class[] classes = new Class[next_classes_len];
            System.arraycopy(genericParameterTypes, this.getGenericArgumentsCount(), classes, 0, next_classes_len);
            key = new StringBuffer().append(GENERIC_PREFIX_KEY).append(String.valueOf(level + 1));
            context.put(key.toString(), (Object) classes);
            return (Class) genericParameterTypes[paramIndex];
        }
        if (genericParameterTypes[paramIndex] instanceof Class) {
            return (Class) genericParameterTypes[paramIndex];
        }
        ParameterizedType ptype = (ParameterizedType) genericParameterTypes[paramIndex];
        Class myCls = (Class) ptype.getRawType();
        genericParameterTypes = ptype.getActualTypeArguments();
        if (genericParameterTypes == null || genericParameterTypes.length == 0) {
            return myCls;
        }
        key = new StringBuffer().append(GENERIC_PREFIX_KEY).append(String.valueOf(level + 1));
        context.put(key.toString(), (Object) genericParameterTypes);
        return myCls;
    }

    public void keepArraySource(OgnlContext context, Object target, int index, int level) {
        StringBuffer key = new StringBuffer();
        key.append(ARRAR_SOURCE_PREFIX_KEY).append(String.valueOf(level + 1));
        ArraySourceContainer a = new ArraySourceContainer();
        a.setIndex(index);
        a.setTarget(target);
        context.put(key.toString(), a);
    }

}
