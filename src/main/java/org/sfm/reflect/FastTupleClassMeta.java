package org.sfm.reflect;

import org.sfm.map.MapperBuildingException;
import org.sfm.reflect.meta.*;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;


public class FastTupleClassMeta<T> implements ClassMeta<T> {

    private final ClassMeta<T> delegate;

    public FastTupleClassMeta(Type target, ReflectionService reflectionService) {

        try {
            Class<T> clazz = TypeHelper.toClass(target);
            final List<ConstructorDefinition<T>> constructorDefinitions = new ArrayList<ConstructorDefinition<T>>();
            constructorDefinitions.add(new ConstructorDefinition<T>(clazz.getConstructor()));
            final List<PropertyMeta<T, ?>> properties = getPropertyMetas(clazz, reflectionService);
            this.delegate = new ObjectClassMeta<T>(target,
                    constructorDefinitions, new ArrayList<>(), properties, reflectionService);
        } catch (NoSuchMethodException e) {
            throw new MapperBuildingException(e.getMessage(), e);
        }
    }

    private ArrayList<PropertyMeta<T, ?>> getPropertyMetas(Class<T> clazz, ReflectionService reflectionService) throws NoSuchMethodException {
        final ArrayList<PropertyMeta<T, ?>> propertyMetas = new ArrayList<PropertyMeta<T, ?>>();

        for(Method m : clazz.getDeclaredMethods()) {
            if (m.getParameterCount() == 0) {
                String field = m.getName();

                Method getter = m;
                Method setter = clazz.getDeclaredMethod(field, getter.getReturnType());

                MethodPropertyMeta<T, ?> propertyMeta = newPropertyMethod(field, getter, setter, reflectionService);
                propertyMetas.add(propertyMeta);
            }
        }


        return propertyMetas;
    }

    private <P> MethodPropertyMeta<T, P> newPropertyMethod(String field, Method getter, Method setter, ReflectionService reflectionService) {
        return new MethodPropertyMeta<T, P>(field, field, reflectionService, setter, getter, getter.getGenericReturnType());
    }

    @Override
    public ReflectionService getReflectionService() {
        return delegate.getReflectionService();
    }

    @Override
    public PropertyFinder<T> newPropertyFinder() {
        return delegate.newPropertyFinder();
    }

    @Override
    public Type getType() {
        return delegate.getType();
    }

    @Override
    public String[] generateHeaders() {
        return delegate.generateHeaders();
    }
}
