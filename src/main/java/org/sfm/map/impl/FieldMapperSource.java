package org.sfm.map.impl;


import org.sfm.map.FieldKey;
import org.sfm.map.GetterFactory;

public interface FieldMapperSource<S, K extends FieldKey<K>> {

    Class<S> source();
    GetterFactory<S, K> getterFactory();


}
